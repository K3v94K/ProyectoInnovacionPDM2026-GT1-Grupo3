# RunningApp - Monitoreo Deportivo GPS

Aplicacion Android para registrar entrenamientos deportivos mediante GPS. Permite iniciar una ruta, visualizar el mapa en tiempo real, medir distancia, tiempo, velocidad promedio y calorias aproximadas, definir metas personalizadas y consultar el historial de entrenamientos realizados.

## Funcionalidades principales

- Registro de rutas deportivas con ubicacion GPS.
- Visualizacion del recorrido sobre Google Maps durante el entrenamiento.
- Metricas en tiempo real: distancia, tiempo, velocidad promedio y calorias.
- Metas personalizadas por distancia, tiempo o calorias.
- Notificacion persistente mientras el entrenamiento esta activo.
- Notificacion local cuando se alcanza una meta.
- Historial de rutas guardadas.
- Consulta de detalle de ruta con trazado GPS real guardado.
- Estadisticas generales con grafica de velocidad promedio por ruta.
- Configuracion de nombre y peso del usuario.

## Tecnologias utilizadas

- Kotlin
- Android SDK
- Gradle
- Android Jetpack
- Room Database
- LiveData y ViewModel
- Navigation Component
- Foreground Services
- Google Maps SDK for Android
- Google Play Services Location
- Material Components

## Librerias principales

- `androidx.appcompat:appcompat`
- `androidx.core:core-ktx`
- `androidx.constraintlayout:constraintlayout`
- `com.google.android.material:material`
- `androidx.lifecycle:lifecycle-service`
- `androidx.lifecycle:lifecycle-viewmodel-ktx`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.room:room-runtime`
- `androidx.room:room-ktx`
- `androidx.navigation:navigation-fragment-ktx`
- `androidx.navigation:navigation-ui-ktx`
- `com.google.android.gms:play-services-location`
- `com.google.android.gms:play-services-maps`
- `com.google.dagger:hilt-android`
- `com.github.bumptech.glide:glide`
- `pub.devrel:easypermissions`
- `com.github.PhilJay:MPAndroidChart`
- `com.jakewharton.timber:timber`

## Descripcion tecnica

La aplicacion usa un `ForegroundService` para mantener el seguimiento activo durante el entrenamiento. El servicio recibe actualizaciones de ubicacion mediante `FusedLocationProviderClient`, actualiza el tiempo de carrera y mantiene la notificacion persistente de entrenamiento en curso.

Los entrenamientos se guardan en Room. Cada registro almacena metricas generales del entrenamiento y, ademas, los puntos GPS reales capturados durante la ruta. Esto permite abrir una ruta del historial y reconstruir su recorrido sobre Google Maps.

Las metas se envian al servicio al iniciar el entrenamiento. El servicio evalua si la meta fue alcanzada, incluso si la app no esta en primer plano, y dispara una notificacion local cuando corresponde.

## Permisos requeridos

- Ubicacion precisa y aproximada para registrar rutas GPS.
- Servicio en primer plano para mantener el seguimiento activo.
- Notificaciones para mostrar el entrenamiento en curso y avisos de meta alcanzada.

## Requisitos de ejecucion

- Android Studio
- JDK compatible con Gradle/Android Studio
- Dispositivo o emulador con Google Play Services
- Clave de Google Maps configurada en los recursos del proyecto

## Uso basico

1. Abrir la app y configurar nombre y peso.
2. Entrar a "Mis rutas" y presionar el boton `+`.
3. Seleccionar una meta por distancia, tiempo o calorias.
4. Presionar "Iniciar" para comenzar el seguimiento.
5. Pausar y finalizar la ruta cuando termine el entrenamiento.
6. Revisar el historial y tocar una ruta para consultar su recorrido guardado.
