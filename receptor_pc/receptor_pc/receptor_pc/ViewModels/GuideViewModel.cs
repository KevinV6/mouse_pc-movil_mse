using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;

namespace receptor_pc.ViewModels;

public sealed record GuideSectionItem(
    string Key,
    string Title,
    string Subtitle,
    IReadOnlyList<string> BulletPoints);

public sealed partial class GuideViewModel : ObservableObject
{
    public ObservableCollection<GuideSectionItem> Sections { get; }

    [ObservableProperty]
    private GuideSectionItem _selectedSection;

    public GuideViewModel()
    {
        Sections =
        [
            new GuideSectionItem(
                "quickstart",
                "1. Inicio rápido",
                "Conecta tu teléfono Android con Windows 11 en menos de 30 segundos.",
                [
                    "Conecta tu PC y tu teléfono Android a la misma red Wi-Fi local (preferiblemente 5 GHz).",
                    "Haz clic en el ícono de MSE Receptor en la bandeja del sistema (junto al reloj de Windows) para ver el código QR y el PIN.",
                    "Abre la app MSE Mouse en tu móvil y toca «Escanear QR» o selecciona tu PC en la lista de detección automática."
                ]),
            new GuideSectionItem(
                "pairing",
                "2. Vincular dispositivos",
                "Tres modos de conexión local con cifrado TLS.",
                [
                    "Código QR (recomendado): Incluye IP, puerto, huella criptográfica del certificado de tu PC y el PIN actual en un solo paso.",
                    "Detección automática (UDP 47801): El móvil descubre tu PC en la red local. Verifica que el código de 8 caracteres (XXXX-XXXX) coincida con el del panel y escribe el PIN de 6 dígitos.",
                    "IP Manual: Ingresa la dirección IPv4 y el puerto (47800 por defecto) que muestra el panel de la PC.",
                    "Una vez vinculado, el teléfono recibe un token persistente y se reconectará automáticamente al abrir la app."
                ]),
            new GuideSectionItem(
                "features",
                "3. Funciones y gestos",
                "Control total de mouse, teclado, multimedia y energía.",
                [
                    "Trackpad (1 dedo): Desliza para mover el cursor; 1 toque = clic izquierdo; doble toque = doble clic; mantén presionado 400 ms y arrastra para mover ventanas o archivos.",
                    "Gestos multi-dedo: Toque con 2 dedos = clic derecho; deslizar con 2 dedos = scroll vertical y horizontal; pellizcar = zoom (Ctrl + Rueda); toque con 3 dedos = clic central.",
                    "Teclado en tiempo real: Envía texto Unicode (acentos, ñ, emojis) al instante y cuenta con barra de teclas especiales (Esc, Tab, Ctrl, Alt, Shift, Win, flechas, F1-F12).",
                    "Multimedia y sistema: Control de volumen, silencio, reproducción, atajos rápidos (Alt+Tab, Copiar/Pegar, Mostrar escritorio) y acciones de energía (Bloquear, Suspender, Apagar)."
                ]),
            new GuideSectionItem(
                "security",
                "4. Seguridad y control",
                "Protección contra accesos no autorizados en redes Wi-Fi compartidas.",
                [
                    "Canal TLS 1.2/1.3 con fijación de certificado (Certificate Pinning): Evita ataques de intermediario (MITM) en la misma red Wi-Fi.",
                    "PIN dinámico: El PIN de 6 dígitos expira cada 5 minutos, rota tras cada vinculación y se bloquea 30 segundos tras 5 intentos fallidos.",
                    "Almacenamiento seguro: En Windows los tokens se guardan como hash SHA-256 cifrado con DPAPI; en Android se cifran con AES-256-GCM en el Android Keystore.",
                    "Revocación instantánea: Puedes desconectar o revocar cualquier teléfono desde el panel de la bandeja o cerrar nuevos emparejamientos con el interruptor correspondiente."
                ]),
            new GuideSectionItem(
                "troubleshooting",
                "5. Solución de problemas",
                "Respuestas rápidas a los casos más comunes en Windows 11.",
                [
                    "El móvil no detecta la PC: Asegúrate de que el perfil de red de Windows esté en «Red privada» y permite el acceso en el Firewall de Windows (puedes ejecutar receptor_pc/tools/add-firewall-rule.ps1 como Administrador).",
                    "El cursor no responde en una ventana específica: Por seguridad de Windows (UIPI), las ventanas ejecutadas como Administrador o los diálogos de UAC solo aceptan entrada de procesos con el mismo nivel de privilegio.",
                    "La IP de la PC cambió: La app móvil buscará automáticamente tu PC por su identificador criptográfico (ServerId) y actualizará la IP sin perder la vinculación."
                ])
        ];

        _selectedSection = Sections[0];
    }
}
