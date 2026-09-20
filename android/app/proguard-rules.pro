# I modelli di dominio non passano più da kotlinx.serialization — a viaggiare
# sono i DTO di :core:network — ma restano usati per riflessione da Compose
# tooling e dai preview, quindi i membri si tengono.
-keepclassmembers class com.devora.mencare.core.model.** { *; }

# DTO dell'API: i nomi dei campi SONO il contratto con il backend. Se R8 li
# rinomina, l'app si mette a mandare `{"a":1}` e il server non capisce più
# niente — e succederebbe solo in release, dove ci si accorge tardi.
-keep class com.devora.mencare.core.network.dto.** { *; }
-keepclassmembers class com.devora.mencare.core.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.devora.mencare.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit crea le implementazioni delle interfacce a runtime: senza le firme
# generiche non sa più che tipo deserializzare.
-keep,allowobfuscation interface com.devora.mencare.core.network.api.*
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
