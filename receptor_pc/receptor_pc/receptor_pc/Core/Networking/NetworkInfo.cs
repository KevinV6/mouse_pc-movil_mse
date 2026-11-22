using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace receptor_pc.Core.Networking;

public static class NetworkInfo
{
    private static readonly string[] VirtualKeywords =
        ["virtual", "hyper-v", "vethernet", "vmware", "vbox", "virtualbox", "wsl", "loopback", "tap-", "tun", "tailscale", "zerotier"];

    public static string GetPreferredLanIp()
    {
        try
        {
            var candidates = new List<(IPAddress Ip, int Score)>();

            foreach (NetworkInterface nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up)
                {
                    continue;
                }

                if (nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel)
                {
                    continue;
                }

                string desc = $"{nic.Name} {nic.Description}".ToLowerInvariant();
                bool isVirtual = VirtualKeywords.Any(k => desc.Contains(k, StringComparison.Ordinal));

                IPInterfaceProperties ipProps = nic.GetIPProperties();
                bool hasGateway = ipProps.GatewayAddresses.Any(g =>
                    g.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !IPAddress.IsLoopback(g.Address) &&
                    !g.Address.Equals(IPAddress.Any));

                foreach (UnicastIPAddressInformation unicast in ipProps.UnicastAddresses)
                {
                    if (unicast.Address.AddressFamily != AddressFamily.InterNetwork)
                    {
                        continue;
                    }

                    byte[] bytes = unicast.Address.GetAddressBytes();
                    // Skip link-local 169.254.x.x
                    if (bytes[0] == 169 && bytes[1] == 254)
                    {
                        continue;
                    }

                    int score = 0;
                    if (hasGateway) score += 50;
                    if (!isVirtual) score += 30;
                    if (nic.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) score += 15;
                    if (nic.NetworkInterfaceType == NetworkInterfaceType.Ethernet) score += 10;
                    if (IsPrivateIpv4(bytes)) score += 10;

                    candidates.Add((unicast.Address, score));
                }
            }

            if (candidates.Count > 0)
            {
                return candidates.OrderByDescending(c => c.Score).First().Ip.ToString();
            }
        }
        catch
        {
            // Fallback below
        }

        return "127.0.0.1";
    }

    private static bool IsPrivateIpv4(byte[] b)
    {
        return b[0] == 10 ||
               (b[0] == 172 && b[1] >= 16 && b[1] <= 31) ||
               (b[0] == 192 && b[1] == 168);
    }
}
