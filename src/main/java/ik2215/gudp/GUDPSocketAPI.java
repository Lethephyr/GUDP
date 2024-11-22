package ik2215.gudp;

import java.io.IOException;
import java.net.DatagramPacket;

public interface GUDPSocketAPI {

    public void send(DatagramPacket packet) throws IOException;

    public void receive(DatagramPacket packet) throws IOException;

    public void finish() throws IOException;

    public void close() throws IOException;
}
