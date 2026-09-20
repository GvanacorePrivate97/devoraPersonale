import Foundation

/// Indirizzo dell'API. In Debug si parla con il backend in esecuzione sul Mac
/// (`http://localhost:3000/v1`, con l'eccezione ATS dichiarata in project.yml);
/// in Release l'indirizzo HTTPS arriva dalle impostazioni di build, cosi' il
/// binario di produzione non contiene nessun riferimento a localhost.
enum ApiEnvironment {

    static var baseURL: URL {
        #if DEBUG
        if let override = Bundle.main.object(forInfoDictionaryKey: "MenCareApiBaseURL") as? String,
           let url = URL(string: override), !override.isEmpty {
            return url
        }
        return URL(string: "http://localhost:3000/v1")!
        #else
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "MenCareApiBaseURL") as? String,
              let url = URL(string: raw), url.scheme == "https" else {
            // Meglio fermarsi al primo avvio che spedire i dati di un cliente su
            // un indirizzo sbagliato o in chiaro.
            fatalError("MenCareApiBaseURL mancante o non HTTPS nelle impostazioni di build")
        }
        return url
        #endif
    }
}

/// Verbo HTTP delle rotte che l'app usa.
enum HTTPMethod: String {
    case get = "GET", post = "POST", patch = "PATCH", put = "PUT", delete = "DELETE"
}

/// Una richiesta verso l'API, descritta dai file `Endpoints*.swift` di ogni area.
struct ApiRequest {
    let method: HTTPMethod
    let path: String
    var query: [String: String?] = [:]
    var body: (any Encodable)?
    /// Le rotte di accesso non hanno (ancora) un token: mandarne uno scaduto
    /// farebbe partire un rinnovo inutile proprio mentre si sta entrando.
    var authenticated = true

    static func get(_ path: String, query: [String: String?] = [:], authenticated: Bool = true) -> ApiRequest {
        ApiRequest(method: .get, path: path, query: query, authenticated: authenticated)
    }

    static func post(_ path: String, body: (any Encodable)? = nil, authenticated: Bool = true) -> ApiRequest {
        ApiRequest(method: .post, path: path, body: body, authenticated: authenticated)
    }

    static func patch(_ path: String, body: (any Encodable)? = nil) -> ApiRequest {
        ApiRequest(method: .patch, path: path, body: body)
    }

    static func put(_ path: String, body: (any Encodable)? = nil) -> ApiRequest {
        ApiRequest(method: .put, path: path, body: body)
    }

    static func delete(_ path: String) -> ApiRequest {
        ApiRequest(method: .delete, path: path)
    }
}

/// Corpo vuoto: `204 No Content` e' una risposta legittima, non un errore.
struct EmptyResponse: Decodable {}

/// Chi viene avvisato quando la sessione cade per davvero (rinnovo rifiutato).
/// L'app riporta l'utente all'accesso: e' l'unica cosa sensata da fare.
protocol SessionObserver: AnyObject, Sendable {
    func sessionDidExpire() async
}

/// Client HTTP dell'app. E' un `actor` per una ragione sola: il rinnovo del
/// token deve essere unico. Dieci schermate che partono insieme e trovano
/// l'access token scaduto devono aspettare lo stesso rinnovo, non farne dieci
/// (che, ruotando il refresh token, si invaliderebbero a vicenda).
actor ApiClient {

    private let baseURL: URL
    private let session: URLSession
    private let tokens: TokenStorage
    private weak var sessionObserver: (any SessionObserver)?

    /// Il rinnovo in corso, se c'e': chi arriva dopo aspetta questo.
    private var refreshTask: Task<AuthTokens, Error>?

    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        // Date e orari viaggiano come stringhe ISO e i tipi di dominio sanno
        // gia' leggersi da soli (vedi `Core/Common/CoreTime.swift`): nessuna
        // strategia globale sulle date, che qui farebbe piu' danni che altro.
        return decoder
    }()

    private let encoder = JSONEncoder()

    init(
        baseURL: URL = ApiEnvironment.baseURL,
        tokens: TokenStorage,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.tokens = tokens
        self.session = session
    }

    func setSessionObserver(_ observer: any SessionObserver) {
        sessionObserver = observer
    }

    // MARK: - Sessione

    var hasSession: Bool { tokens.read() != nil }

    func store(_ newTokens: AuthTokens) {
        tokens.write(newTokens)
    }

    func clearSession() {
        refreshTask?.cancel()
        refreshTask = nil
        tokens.clear()
    }

    /// Il refresh token serve al logout esplicito: e' l'unico punto in cui esce
    /// dal portachiavi, e va nel corpo della richiesta — mai in query string,
    /// finirebbe nei log del server.
    var refreshToken: String? { tokens.read()?.refreshToken }

    // MARK: - Chiamate

    func send<T: Decodable>(_ request: ApiRequest, as type: T.Type) async throws -> T {
        let data = try await perform(request)
        if data.isEmpty, let empty = EmptyResponse() as? T { return empty }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            // Un corpo che non combacia con il contratto e' un errore nostro,
            // non dell'utente: si dice "qualcosa non va", non si finge un dato.
            throw AppError.unknown(message: "Risposta non valida dal server")
        }
    }

    @discardableResult
    func send(_ request: ApiRequest) async throws -> Data {
        try await perform(request)
    }

    /// Caricamento multipart, per la foto di profilo.
    func upload<T: Decodable>(
        path: String, fileData: Data, fileName: String, mimeType: String, as type: T.Type
    ) async throws -> T {
        let boundary = "mencare-\(UUID().uuidString)"
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        let data = try await performRaw(
            path: path, method: .post, query: [:], body: body,
            contentType: "multipart/form-data; boundary=\(boundary)",
            authenticated: true, allowRefresh: true
        )
        if data.isEmpty, let empty = EmptyResponse() as? T { return empty }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw AppError.unknown(message: "Risposta non valida dal server")
        }
    }

    // MARK: - Esecuzione

    private func perform(_ request: ApiRequest) async throws -> Data {
        var encodedBody: Data?
        if let body = request.body {
            encodedBody = try encoder.encode(AnyEncodable(body))
        }
        return try await performRaw(
            path: request.path, method: request.method, query: request.query,
            body: encodedBody, contentType: encodedBody == nil ? nil : "application/json",
            authenticated: request.authenticated, allowRefresh: true
        )
    }

    private func performRaw(
        path: String, method: HTTPMethod, query: [String: String?], body: Data?,
        contentType: String?, authenticated: Bool, allowRefresh: Bool
    ) async throws -> Data {
        var accessToken: String?
        if authenticated {
            accessToken = try await validAccessToken()
        }

        let urlRequest = try buildRequest(
            path: path, method: method, query: query, body: body,
            contentType: contentType, accessToken: accessToken
        )

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch {
            throw ApiError.appError(from: error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw AppError.unknown(message: "Risposta non valida dal server")
        }

        if (200..<300).contains(http.statusCode) { return data }

        // 401 su una chiamata autenticata: l'access token puo' essere stato
        // revocato prima della scadenza. Un solo tentativo di rinnovo, poi si
        // esce — cosi' non si entra in un ciclo di rinnovi.
        if http.statusCode == 401, authenticated, allowRefresh {
            do {
                _ = try await refreshTokens()
            } catch {
                await expireSession()
                throw AppError.unauthorized
            }
            return try await performRaw(
                path: path, method: method, query: query, body: body,
                contentType: contentType, authenticated: true, allowRefresh: false
            )
        }

        if let decoded = try? decoder.decode(ApiErrorBody.self, from: data) {
            let appError = ApiError.appError(from: decoded, status: http.statusCode)
            // Un 401 che arriva fin qui vuol dire che nemmeno il rinnovo ha
            // aiutato: la sessione e' finita.
            if case .unauthorized = appError, authenticated { await expireSession() }
            throw appError
        }
        throw ApiError.appError(status: http.statusCode)
    }

    private func buildRequest(
        path: String, method: HTTPMethod, query: [String: String?], body: Data?,
        contentType: String?, accessToken: String?
    ) throws -> URLRequest {
        var components = URLComponents(
            url: baseURL.appending(path: path.hasPrefix("/") ? String(path.dropFirst()) : path),
            resolvingAgainstBaseURL: false
        )
        let items = query.compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
        if !items.isEmpty { components?.queryItems = items.sorted { $0.name < $1.name } }
        guard let url = components?.url else { throw AppError.unknown(message: "URL non valido") }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }
        return request
    }

    // MARK: - Rinnovo a volo singolo

    private func validAccessToken() async throws -> String {
        guard let current = tokens.read() else { throw AppError.unauthorized }
        if !current.isExpired() { return current.accessToken }
        return try await refreshTokens().accessToken
    }

    /// Un rinnovo alla volta. Se ce n'e' gia' uno in volo si aspetta quello:
    /// il refresh token ruota a ogni uso, due rinnovi paralleli si
    /// annullerebbero a vicenda e butterebbero fuori l'utente.
    private func refreshTokens() async throws -> AuthTokens {
        if let refreshTask {
            return try await refreshTask.value
        }
        guard let current = tokens.read() else {
            await expireSession()
            throw AppError.unauthorized
        }

        let task = Task<AuthTokens, Error> { [baseURL, session, decoder, encoder] in
            var request = URLRequest(url: baseURL.appending(path: "auth/refresh"))
            request.httpMethod = HTTPMethod.post.rawValue
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            request.httpBody = try encoder.encode(RefreshRequestDto(refreshToken: current.refreshToken))

            let data: Data
            let response: URLResponse
            do {
                (data, response) = try await session.data(for: request)
            } catch {
                throw ApiError.appError(from: error)
            }
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw AppError.unauthorized
            }
            let decoded = try decoder.decode(AuthResultDto.self, from: data)
            return decoded.tokens.toDomain()
        }
        refreshTask = task

        defer { refreshTask = nil }
        do {
            let renewed = try await task.value
            tokens.write(renewed)
            return renewed
        } catch {
            // Rinnovo rifiutato: i token vanno buttati subito, restare con una
            // coppia morta in portachiavi farebbe fallire ogni schermata.
            tokens.clear()
            throw error
        }
    }

    private func expireSession() async {
        tokens.clear()
        await sessionObserver?.sessionDidExpire()
    }
}

/// `Encodable` esistenziale: i corpi delle richieste sono DTO diversi, e
/// `JSONEncoder` vuole un tipo concreto.
private struct AnyEncodable: Encodable {
    private let write: (Encoder) throws -> Void

    init(_ wrapped: any Encodable) {
        write = { encoder in try wrapped.encode(to: encoder) }
    }

    func encode(to encoder: Encoder) throws {
        try write(encoder)
    }
}
