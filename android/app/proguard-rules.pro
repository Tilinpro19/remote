# OkHttp publica sus propias reglas de consumidor; sólo silenciamos plataformas opcionales.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Paho carga sus módulos de red (tcp://, ssl://) por ServiceLoader y reflexión.
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
