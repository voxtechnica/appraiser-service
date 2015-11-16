package info.voxtechnica.appraisers.model;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.net.InetAddress;
import java.util.Comparator;
import java.util.Date;

/**
 * A "Node" is an identifier for an application node. The nodeId is used by the TuidFactory to ensure cluster-unique
 * identifiers.
 */
@Data
public class Node implements Comparable<Node> {
    private byte id;
    private InetAddress ipAddress;
    private Date timestamp;

    public Node() {
    }

    public Node(byte id, InetAddress ipAddress, Date timestamp) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(Node that) {
        return NodeIdOrder.compare(this, that);
    }

    public static Comparator<Node> NodeIdOrder = new Comparator<Node>() {
        @Override
        public int compare(Node one, Node two) {
            if (one == null && two == null) return 0;
            int c0 = one == null ? -1 : (two == null ? 1 : 0);
            if (c0 != 0) return c0;
            return ObjectUtils.compare(one.getId(), two.getId());
        }
    };

    public static Comparator<Node> IpAddressOrder = new Comparator<Node>() {
        @Override
        public int compare(Node one, Node two) {
            if (one == null && two == null) return 0;
            int c = one == null ? -1 : (two == null ? 1 : 0);
            if (c != 0) return c;
            return ObjectUtils.compare(one.getIpAddress().toString(), two.getIpAddress().toString());
        }
    };

}
