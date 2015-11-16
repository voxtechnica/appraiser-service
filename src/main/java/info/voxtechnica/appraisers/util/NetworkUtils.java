package info.voxtechnica.appraisers.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.TreeMap;

public class NetworkUtils {

    /**
     * Get the IP address of a specified network interface. Use "auto" for the first available non-local address and
     * "any" to include local loopback addresses as acceptable.
     *
     * @param networkInterface string, such as "eth0"
     * @return IP Address
     * @throws SocketException
     */
    public static InetAddress getIpAddress(String networkInterface) throws SocketException {
        boolean loopbackAcceptable = "any".equalsIgnoreCase(networkInterface);
        boolean firstAvailable = loopbackAcceptable || "auto".equalsIgnoreCase(networkInterface);
        TreeMap<String, InetAddress> addresses = getInterfaceAddresses(loopbackAcceptable);
        return addresses.isEmpty() ? null : firstAvailable ? addresses.firstEntry().getValue() : addresses.get(networkInterface);
    }

    public static InetAddress getIpAddressByName(String networkInterface) throws SocketException {
        InetAddress ipAddress = null;
        NetworkInterface nif = NetworkInterface.getByName(networkInterface);
        if (nif != null) for (InetAddress address : Collections.list(nif.getInetAddresses())) {
            if (!address.isLinkLocalAddress() && !address.isLoopbackAddress()) {
                ipAddress = address;
                break;
            }
        }
        return ipAddress;
    }

    /**
     * Get a map of available network interface names and corresponding IP addresses
     *
     * @return network interface-address map
     * @throws SocketException
     */
    public static TreeMap<String, InetAddress> getInterfaceAddresses(Boolean loopbackAcceptable) throws SocketException {
        TreeMap<String, InetAddress> addresses = new TreeMap<>();
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces()))
            for (InetAddress address : Collections.list(nif.getInetAddresses()))
                if (!address.isLinkLocalAddress() && (!address.isLoopbackAddress() || loopbackAcceptable))
                    addresses.put(nif.getName(), address);
        return addresses;
    }

}
