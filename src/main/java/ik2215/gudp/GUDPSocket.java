package ik2215.gudp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Random;

public class GUDPSocket implements GUDPSocketAPI {
	DatagramSocket datagramSocket; // a socket for sending and receiving datagram packets
	LinkedList<GUDPEndPoint> senderList; // list of the send queues, one element per destination (remoteEndPoint)
	LinkedList<GUDPEndPoint> receiverList; // list of the receive queues, one element per destination (remoteEndPoint)
	SenderThread s; // Thread sending packets from send queues to destinations
	ReceiverThread r; // Thread receiving packets and putting them into corresponding receive queues
	/*
	 * Variables below are for testing. You don't need to use them.
	 */
	private boolean debug = true;

	public enum drop {
		NOTHING,
		FIRST_BSN,
		FIRST_DATA,
		FIRST_ACK,
		FIRST_FIN,
		RANDOM,
		ALL,
	}

	private drop senderDrop = drop.NOTHING;
	private drop receiverDrop = drop.NOTHING;

	public GUDPSocket(DatagramSocket socket) {
		datagramSocket = socket;
		/*
		 * - initialize senderList and receiverList
		 * - initialize s and r
		 * - start s and r threads
		 */
		this.senderList = new LinkedList<GUDPEndPoint>();
		this.receiverList = new LinkedList<GUDPEndPoint>();
		this.s = new SenderThread(socket, senderList, senderDrop);
		this.s.setName("SenderThread");
		this.r = new ReceiverThread(socket, receiverList, s, senderList, senderDrop, receiverDrop);
		this.r.setName("ReceiverThread");
		this.s.start();
		System.out.println("SenderThread started");
		this.r.start();
		System.out.println("ReceiverThread started");
	}

	public void send(DatagramPacket packet) throws IOException {
		/*
		 * Find the corresponding remoteEndPoint (address and port) in the senderList
		 * If it does not exist, create one and put a BSN packet as the first packet in
		 * the queue
		 * Then, encapsulate the packet into the GUDP DATA packet and put it in the
		 * queue
		 * Also, notify SenderThread that there are packets to be sent
		 * Remember that the send method is non-blocking and should be executed without
		 * wait/sleep.
		 *
		 * You should also check if, for some unknown reason, the SenderThread is
		 * terminated,
		 * in this case, you should gracefully terminate the program.
		 *
		 * Multiple threads may update senderList, thus you should synchronize it to
		 * avoid race conditions.
		 * You may read up on wait and notify() Methods and Producer-Consumer Problem in
		 * Java below:
		 * https://www.baeldung.com/java-wait-notify
		 * https://www.baeldung.com/java-producer-consumer-problem
		 */
		GUDPEndPoint endPoint = null;
		for (GUDPEndPoint ep : senderList) {
			InetSocketAddress remoteEndPoint = ep.getRemoteEndPoint();
			if (remoteEndPoint.getAddress().equals(packet.getAddress()) &&
					remoteEndPoint.getPort() == packet.getPort()) {
				endPoint = ep;
				break;
			}
		}
		GUDPPacket gudppacket;
		if (endPoint == null) {
			endPoint = new GUDPEndPoint(packet.getAddress(), packet.getPort());
			synchronized (senderList) {
				senderList.add(endPoint);
				ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
				buffer.order(ByteOrder.BIG_ENDIAN);
				gudppacket = new GUDPPacket(buffer);
				gudppacket.setType(GUDPPacket.TYPE_BSN);
				gudppacket.setVersion(GUDPPacket.GUDP_VERSION);
				gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
				gudppacket.setPayloadLength(0);
				Random random = new Random();
				int rand = random.nextInt(Short.MAX_VALUE);
				gudppacket.setSeqno(rand);
				endPoint.setNextseqnum(rand);
				endPoint.setBase(rand);
				endPoint.setLast(rand);
				endPoint.add(gudppacket);
				senderList.notify();
			}
		} else if (endPoint.getFinished()) {
			synchronized (senderList) {
				endPoint.setFinished(false);
				ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
				buffer.order(ByteOrder.BIG_ENDIAN);
				gudppacket = new GUDPPacket(buffer);
				gudppacket.setType(GUDPPacket.TYPE_BSN);
				gudppacket.setVersion(GUDPPacket.GUDP_VERSION);
				gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
				gudppacket.setPayloadLength(0);
				Random random = new Random();
				int rand = random.nextInt(Short.MAX_VALUE);
				gudppacket.setSeqno(rand);
				endPoint.setNextseqnum(rand);
				endPoint.setBase(rand);
				endPoint.setLast(rand);
				endPoint.add(gudppacket);
				senderList.notify();
			}
		}
		synchronized (senderList) {
			gudppacket = GUDPPacket.encapsulate(packet);
			gudppacket.setSeqno(endPoint.getLast() + 1);
			endPoint.setLast(endPoint.getLast() + 1);
			endPoint.add(gudppacket);
			senderList.notify();
		}
	}

	public void receive(DatagramPacket packet) throws IOException {
		/*
		 * iterate through the receiverList to fetch a packet from exsiting
		 * remoteEndPoints
		 * If no packet in the receiverList, then wait for ReceiverThread to notify when
		 * a packet arrives
		 * Then, repeat the same process
		 * Also, if the receiverThread is terminated, you need to gracefully terminate
		 * the program.
		 *
		 * Multiple threads may update receiverList, thus you should synchronize it to
		 * avoid race conditions.
		 */
		synchronized (receiverList) {
			while (true) {
				for (GUDPEndPoint endPoint : receiverList) {
					if (!endPoint.isEmptyQueue()) {
						GUDPPacket gudppacket = endPoint.remove();
						gudppacket.decapsulate(packet);
						return;
					}
				}

				try {
					receiverList.wait();
				} catch (InterruptedException e) {
					throw new IOException("Receive thread interrupted");
				}
			}
		}
	}

	public void finish() throws IOException {
		/*
		 * Create a FIN packet for every remoteEndPoint
		 * Notify SenderThread of new packets in the send queues
		 * then, wait for SenderThread to finish sending all packets (you will loop
		 * through SenderList to verify this)
		 * or it terminates due to reaching max retries
		 * You should also slow down before repeat the loop in case the SenderThread
		 * terminates
		 * (the test code sleep 200 ms before it checks whether SenderThread is still
		 * running)
		 * If SenderThread terminates, you will gracefully terminate the progrm.
		 * Otherwise, the application will call close() to finish the program after
		 * successful transmission.
		 */
		synchronized (senderList) {
			for (GUDPEndPoint gudpEndPoint : senderList) {
				if (!gudpEndPoint.getFinished()) {
					ByteBuffer buffer = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
					buffer.order(ByteOrder.BIG_ENDIAN);
					GUDPPacket gudpPacket = new GUDPPacket(buffer);
					gudpPacket.setVersion(GUDPPacket.GUDP_VERSION);
					gudpPacket.setType(GUDPPacket.TYPE_FIN);
					gudpPacket.setSocketAddress(gudpEndPoint.getRemoteEndPoint());
					gudpPacket.setPayloadLength(0);
					int seqNumber = (gudpEndPoint.getLast() + 1);
					gudpPacket.setSeqno(seqNumber);
					gudpEndPoint.setLast(gudpEndPoint.getLast() + 1);
					gudpEndPoint.add(gudpPacket);
				}
			}
			senderList.notifyAll();
		}
		while (true) {
			synchronized (senderList) {
				boolean allPacketsSent = true;
				for (GUDPEndPoint gudpEndPoint : senderList) {
					if (!gudpEndPoint.isEmptyQueue() || gudpEndPoint.getBase() < gudpEndPoint.getLast()) {
						allPacketsSent = false;
						break;
					}
				}

				if (allPacketsSent) {
					for (GUDPEndPoint endPoint : senderList) {
						endPoint.setFinished(true);
					}
					break;
				}
			}

			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			if (!s.isAlive()) {
				close();
				System.exit(1);
			}
		}
	}

	public void close() throws IOException {
		/*
		 * terminate GUDP gracefully by stopping sender and receiver threads and close
		 * the socket
		 */
		synchronized (senderList) {
			s.stopSenderThread();
			senderList.notifyAll();
		}
		synchronized (receiverList) {
			r.stopReceiverThread();
			receiverList.notifyAll();
		}
		datagramSocket.close();
	}
}
