using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Text.Json;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;

namespace receptor_pc.DevClient;

internal sealed record SavedDevProfile(
    string DeviceId,
    string Host,
    int Port,
    string Token,
    string Fingerprint,
    string ServerName);

internal static class Program
{
    private static readonly string ProfilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "MseDevClient",
        "dev_profile.json");

    public static async Task<int> Main(string[] args)
    {
        if (args.Length == 0)
        {
            PrintUsage();
            return 1;
        }

        string cmd = args[0].ToLowerInvariant();
        try
        {
            switch (cmd)
            {
                case "discover":
                    await RunDiscoverAsync();
                    return 0;

                case "pair":
                    if (args.Length < 3)
                    {
                        Console.Error.WriteLine("Uso: pair <host> <pin> [port]");
                        return 1;
                    }
                    int pairPort = args.Length >= 4 && int.TryParse(args[3], out int p) ? p : ProtocolConstants.TcpPort;
                    return await RunPairAsync(args[1], pairPort, args[2]);

                case "auth":
                {
                    SavedDevProfile profile = LoadProfileOrThrow();
                    string host = args.Length >= 2 ? args[1] : profile.Host;
                    return await RunAuthenticatedCommandAsync(profile with { Host = host }, async _ =>
                    {
                        Console.WriteLine("Autenticación por token verificada correctamente.");
                        await Task.CompletedTask;
                    });
                }

                case "ping":
                {
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        long ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new PingMessage(ts)));
                        RawFrame? reply = await FrameCodec.ReadFrameAsync(ssl);
                        if (reply is { KnownType: FrameType.Json } &&
                            FrameCodec.DecodeJson(reply.Value.Payload) is PongMessage pong)
                        {
                            long rtt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - pong.Ts;
                            Console.WriteLine($"PONG recibido (RTT={rtt} ms)");
                        }
                    });
                }

                case "move":
                {
                    if (args.Length < 3 || !short.TryParse(args[1], out short dx) || !short.TryParse(args[2], out short dy))
                    {
                        Console.Error.WriteLine("Uso: move <dx> <dy>");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeMouseMove(dx, dy));
                        Console.WriteLine($"Enviado MOUSE_MOVE dx={dx} dy={dy}");
                    });
                }

                case "scroll":
                {
                    if (args.Length < 3 || !short.TryParse(args[1], out short dx) || !short.TryParse(args[2], out short dy))
                    {
                        Console.Error.WriteLine("Uso: scroll <dx> <dy>");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeScroll(dx, dy));
                        Console.WriteLine($"Enviado SCROLL dx={dx} dy={dy}");
                    });
                }

                case "zoom":
                {
                    if (args.Length < 2 || !short.TryParse(args[1], out short steps))
                    {
                        Console.Error.WriteLine("Uso: zoom <steps>");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeZoom(steps));
                        Console.WriteLine($"Enviado ZOOM steps={steps}");
                    });
                }

                case "click":
                {
                    string button = args.Length >= 2 ? args[1] : "left";
                    int count = args.Length >= 3 && int.TryParse(args[2], out int c) ? c : 1;
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ClickMessage(button, count)));
                        Console.WriteLine($"Enviado CLICK button={button} count={count}");
                    });
                }

                case "drag-demo":
                {
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ButtonMessage("left", "down")));
                        for (int i = 0; i < 50; i++)
                        {
                            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeMouseMove(4, 2));
                            await Task.Delay(8);
                        }
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ButtonMessage("left", "up")));
                        Console.WriteLine("Drag demo completado.");
                    });
                }

                case "text":
                {
                    if (args.Length < 2)
                    {
                        Console.Error.WriteLine("Uso: text \"...\"");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new TextMessage(args[1])));
                        Console.WriteLine($"Enviado TEXT: {args[1]}");
                    });
                }

                case "key":
                {
                    if (args.Length < 2)
                    {
                        Console.Error.WriteLine("Uso: key <NAME> [press|down|up]");
                        return 1;
                    }
                    string action = args.Length >= 3 ? args[2] : "press";
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new KeyMessage(args[1], action)));
                        Console.WriteLine($"Enviado KEY {args[1]} ({action})");
                    });
                }

                case "combo":
                {
                    if (args.Length < 2)
                    {
                        Console.Error.WriteLine("Uso: combo <KEY1> <KEY2> ...");
                        return 1;
                    }
                    string[] keys = args.Skip(1).ToArray();
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ComboMessage(keys)));
                        Console.WriteLine($"Enviado COMBO {string.Join('+', keys)}");
                    });
                }

                case "media":
                {
                    if (args.Length < 2)
                    {
                        Console.Error.WriteLine("Uso: media <vol_up|vol_down|mute|play_pause|next|prev|stop>");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new MediaMessage(args[1])));
                        Console.WriteLine($"Enviado MEDIA {args[1]}");
                    });
                }

                case "system":
                {
                    if (args.Length < 2)
                    {
                        Console.Error.WriteLine("Uso: system <lock|sleep|shutdown|restart>");
                        return 1;
                    }
                    SavedDevProfile profile = LoadProfileOrThrow();
                    return await RunAuthenticatedCommandAsync(profile, async ssl =>
                    {
                        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new SystemMessage(args[1], true)));
                        Console.WriteLine($"Enviado SYSTEM {args[1]}");
                    });
                }

                default:
                    PrintUsage();
                    return 1;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Error: {ex.Message}");
            return 2;
        }
    }

    private static async Task RunDiscoverAsync()
    {
        using var udp = new UdpClient(AddressFamily.InterNetwork);
        udp.EnableBroadcast = true;

        byte[] payload = JsonSerializer.SerializeToUtf8Bytes<ProtocolMessage>(
            new DiscoverMessage(ProtocolConstants.ProtocolVersion),
            ProtocolJsonContext.Relaxed.ProtocolMessage);

        var broadcastEp = new IPEndPoint(IPAddress.Broadcast, ProtocolConstants.DiscoveryPort);
        for (int i = 0; i < 3; i++)
        {
            await udp.SendAsync(payload, broadcastEp);
            if (i < 2) await Task.Delay(150);
        }

        using var cts = new CancellationTokenSource(TimeSpan.FromMilliseconds(1500));
        var seen = new HashSet<string>(StringComparer.Ordinal);
        while (!cts.IsCancellationRequested)
        {
            try
            {
                UdpReceiveResult res = await udp.ReceiveAsync(cts.Token);
                if (JsonSerializer.Deserialize(res.Buffer, ProtocolJsonContext.Relaxed.ProtocolMessage) is DiscoverReplyMessage reply &&
                    seen.Add(reply.ServerId))
                {
                    Console.WriteLine($"[ENCONTRADO] {reply.ServerName} ({reply.ServerId}) -> {reply.Host}:{reply.Port} | FP={reply.Fp} | PairingOpen={reply.PairingOpen}");
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }

        if (seen.Count == 0)
        {
            Console.WriteLine("No se encontraron servidores MSE en la red local.");
        }
    }

    private static async Task<int> RunPairAsync(string host, int port, string pin)
    {
        string deviceId = TryLoadProfile()?.DeviceId ?? $"devclient-{Guid.NewGuid():N}"[..22];
        string observedFp = string.Empty;

        using var tcp = new TcpClient { NoDelay = true };
        await tcp.ConnectAsync(host, port);

        using var ssl = new SslStream(tcp.GetStream(), false, (_, cert, _, _) =>
        {
            if (cert is null) return false;
            observedFp = CryptoUtils.Sha256Base64Url(cert.GetRawCertData());
            return true;
        });

        await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
        {
            TargetHost = host,
            EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
        });

        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
            new HelloMessage(ProtocolConstants.ProtocolVersion, deviceId, "MSE-DevClient", "windows-cli", "1.0")));

        RawFrame? ackFrame = await FrameCodec.ReadFrameAsync(ssl);
        if (ackFrame is null || FrameCodec.DecodeJson(ackFrame.Value.Payload) is not HelloAckMessage ack)
        {
            Console.Error.WriteLine("No se recibió hello_ack del servidor.");
            return 1;
        }

        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
            new PairRequestMessage(deviceId, "MSE-DevClient", pin)));

        RawFrame? respFrame = await FrameCodec.ReadFrameAsync(ssl);
        if (respFrame is null)
        {
            Console.Error.WriteLine("Conexión cerrada sin respuesta de emparejamiento.");
            return 1;
        }

        ProtocolMessage resp = FrameCodec.DecodeJson(respFrame.Value.Payload);
        if (resp is PairOkMessage ok)
        {
            var profile = new SavedDevProfile(deviceId, host, port, ok.Token, observedFp, ok.ServerName);
            SaveProfile(profile);
            Console.WriteLine($"PAIR_OK con {ok.ServerName} ({ok.ServerId}). Token guardado localmente.");
            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ByeMessage("client_exit")));
            return 0;
        }

        if (resp is PairFailMessage fail)
        {
            Console.Error.WriteLine($"PAIR_FAIL reason={fail.Reason} attemptsLeft={fail.AttemptsLeft} retryAfterSec={fail.RetryAfterSec}");
            return 1;
        }

        Console.Error.WriteLine($"Respuesta inesperada: {resp}");
        return 1;
    }

    private static async Task<int> RunAuthenticatedCommandAsync(
        SavedDevProfile profile,
        Func<SslStream, Task> action)
    {
        using var tcp = new TcpClient { NoDelay = true };
        await tcp.ConnectAsync(profile.Host, profile.Port);

        using var ssl = new SslStream(tcp.GetStream(), false, (_, cert, _, _) =>
        {
            if (cert is null) return false;
            string fp = CryptoUtils.Sha256Base64Url(cert.GetRawCertData());
            return CryptoUtils.FixedTimeEqualsUtf8(fp, profile.Fingerprint);
        });

        await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
        {
            TargetHost = profile.Host,
            EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
        });

        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
            new HelloMessage(ProtocolConstants.ProtocolVersion, profile.DeviceId, "MSE-DevClient", "windows-cli", "1.0")));

        RawFrame? ackFrame = await FrameCodec.ReadFrameAsync(ssl);
        if (ackFrame is null || FrameCodec.DecodeJson(ackFrame.Value.Payload) is not HelloAckMessage)
        {
            Console.Error.WriteLine("Fallo en hello_ack.");
            return 1;
        }

        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
            new AuthMessage(profile.DeviceId, profile.Token)));

        RawFrame? authRespFrame = await FrameCodec.ReadFrameAsync(ssl);
        if (authRespFrame is null)
        {
            Console.Error.WriteLine("Sin respuesta a auth.");
            return 1;
        }

        ProtocolMessage authResp = FrameCodec.DecodeJson(authRespFrame.Value.Payload);
        if (authResp is AuthFailMessage fail)
        {
            Console.Error.WriteLine($"AUTH_FAIL reason={fail.Reason}");
            return 1;
        }

        if (authResp is not AuthOkMessage)
        {
            Console.Error.WriteLine($"Respuesta inesperada: {authResp}");
            return 1;
        }

        await action(ssl);
        await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ByeMessage("client_exit")));
        return 0;
    }

    private static SavedDevProfile? TryLoadProfile()
    {
        if (!File.Exists(ProfilePath)) return null;
        try
        {
            return JsonSerializer.Deserialize<SavedDevProfile>(File.ReadAllText(ProfilePath));
        }
        catch
        {
            return null;
        }
    }

    private static SavedDevProfile LoadProfileOrThrow() =>
        TryLoadProfile() ?? throw new InvalidOperationException("No hay perfil emparejado. Ejecuta primero: pair <host> <pin>");

    private static void SaveProfile(SavedDevProfile profile)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(ProfilePath)!);
        File.WriteAllText(ProfilePath, JsonSerializer.Serialize(profile, new JsonSerializerOptions { WriteIndented = true }));
    }

    private static void PrintUsage()
    {
        Console.WriteLine("MSE DevClient comandos:");
        Console.WriteLine("  discover");
        Console.WriteLine("  pair <host> <pin> [port]");
        Console.WriteLine("  auth [host]");
        Console.WriteLine("  ping");
        Console.WriteLine("  move <dx> <dy>");
        Console.WriteLine("  scroll <dx> <dy>");
        Console.WriteLine("  zoom <steps>");
        Console.WriteLine("  click [left|right|middle] [count]");
        Console.WriteLine("  drag-demo");
        Console.WriteLine("  text \"texto\"");
        Console.WriteLine("  key <NAME> [press|down|up]");
        Console.WriteLine("  combo <KEY1> <KEY2> ...");
        Console.WriteLine("  media <vol_up|vol_down|mute|play_pause|next|prev|stop>");
        Console.WriteLine("  system <lock|sleep|shutdown|restart>");
    }
}
