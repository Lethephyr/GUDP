import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.util.LinkedList;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/*
 * ReceiverThread is a non-static nested class (inner class)
 * ReceiverThread receives incoming packets from remote hosts and put them in the receiverList.
 */
public class ReceiverThread extends Thread {
	private final DatagramSocket sock;
	private final LinkedList<GUDPEndPoint> receiverList;
	private final SenderThread s;
	private final LinkedList<GUDPEndPoint> senderList;
	private boolean runFlag = true;
	private boolean debug = true;
	private GUDPSocket.drop senderDrop;
	private GUDPSocket.drop receiverDrop;

	public ReceiverThread(DatagramSocket sock, LinkedList<GUDPEndPoint> receiverList, SenderThread s,
			LinkedList<GUDPEndPoint> senderList, GUDPSocket.drop senderDrop, GUDPSocket.drop receiverDrop) {
		this.sock = sock;
		this.receiverList = receiverList;
		this.s = s;
		this.senderList = senderList;
		this.senderDrop = senderDrop;
		this.receiverDrop = receiverDrop;
	}

	public void stopReceiverThread() {
		this.runFlag = false;
	}

	@Override
	public void run() {
		while (this.runFlag) {
			/*
			 * This is a loop that continously runs until the ReceiveThread is terminated.
			 * Receive incoming packets and process each packet according to the type of
			 * packets
			 * ACK: Receive ACK as a part of GBN sender logic. Remove all ACKed packets from
			 * senderList.
			 * Progress to RCV and call FSMSender. Also notify senderList
			 * BSN: Create a new remoteEndPoint if it does not exist. Then, add BSN to its
			 * receive queue and send ACK.
			 * If existing remoteEndPoint was already finished. Reset remoteEndPoint and add
			 * the BSN and send ACK.
			 * Otherwise, just send ACK with the expected sequence number.
			 * DATA: If DATA packet with expected sequence number, add it to receive queue
			 * and send ACK.
			 * Otherwise, just send ACK with the expected sequence number.
			 * (If DATA packet for non-existing remoteEndPoint arrives, do nothing)
			 * FIN: Same as DATA. But also set "finished" to true to indicate file reception
			 * is completed.
			 *
			 * You need to synchronize receiverList in all cases except ACK where you need
			 * to synchornize senderList.
			 *
			 * IMPORTANT:
			 * If a BSN of a new tranmission is lost, DATA may arrive before you reset the
			 * remoteEndPoint.
			 * Thus, you should check that the DATA seq is within the expected range based
			 * on the Window size.
			 * Otherwise, you can silently ignore the DATA packet without sending an ACK.
			 */
			byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
			DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
			try {
				this.sock.receive(udppacket);
			} catch (IOException e) {
				System.err.println("ReceiverThread IOException: probably because of socket closed");
				break;
			}

			try {
				GUDPPacket gudppacket = GUDPPacket.unpack(udppacket);
				InetSocketAddress sock = gudppacket.getSocketAddress();

				// InetSocketAddress sock = gudppacket.getSocketAddress();
				GUDPEndPoint endPoint;

				synchronized (receiverList) {
					if (receiverDrop == GUDPSocket.drop.ALL) {
						switch (gudppacket.getType()) {
							case GUDPPacket.TYPE_BSN:
								System.out.println("\t RCV BSN\t" + String.valueOf(gudppacket.getSeqno()) + "\tDROP!");
								break;
							case GUDPPacket.TYPE_DATA:
								System.out.println("\t RCV DATA\t" + String.valueOf(gudppacket.getSeqno()) + "\tDROP!");
								break;
							case GUDPPacket.TYPE_ACK:
								System.out.println("\t RCV ACK\t" + String.valueOf(gudppacket.getSeqno()) + "\tDROP!");
								break;
							case GUDPPacket.TYPE_FIN:
								System.out.println("\t RCV FIN\t" + String.valueOf(gudppacket.getSeqno()) + "\tDROP!");
								break;
						}
						continue;
					}
				} /* synchronized (receiveList) */

				// synchronized (receiverList) {
				switch (gudppacket.getType()) {
					case GUDPPacket.TYPE_ACK:
						// ACK receives only as a response to GBN sender in senderList
						synchronized (senderList) {
							endPoint = getEndPoint(senderList, sock.getAddress(), sock.getPort());
							if (endPoint != null) {
								boolean sendReceiveACK = true;
								switch (senderDrop) {
									case NOTHING:
									case FIRST_BSN:
									case FIRST_DATA:
									case FIRST_FIN:
										break;
									case FIRST_ACK:
										// drop ACK for the first packet arrived at the sender
										if (!endPoint.getDropSend()) {
											sendReceiveACK = false;
											endPoint.setDropSend(true);
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											sendReceiveACK = false;
										}
										break;
									case ALL:
										sendReceiveACK = false;
										break;
								}

								if ((sendReceiveACK) && (endPoint.getBase() <= gudppacket.getSeqno())
										&& (gudppacket.getSeqno() - 1 <= endPoint.getLast())) {
									if (debug) {
										System.err.println("\t RCV ACK\t" + (gudppacket.getSeqno()));
									}
									endPoint.removeAllACK(gudppacket.getSeqno() - 1);
									endPoint.setBase(gudppacket.getSeqno());
									endPoint.setState(GUDPEndPoint.endPointState.RCV);
									s.FSMSender(endPoint);
									senderList.notify();
								} else {
									System.err.println("\t RCV ACK\t" + (gudppacket.getSeqno()) + "\tDROP!");
								}
							} else {
								// no remoteEndPoint: do nothing
								System.err.println(
										"\t RCV ACK\t" + (gudppacket.getSeqno()) + "\tNO MATCHING END POINT. DROP!");
							}
						} /* synchronized (senderList) */
						break;

					case GUDPPacket.TYPE_BSN:
						// BSN signifies a new connection from the remoteEndPoint
						// Add it to receiverList if it is really a new remoteEndPoint
						// Otherwise, ignore the BSN packet
						synchronized (receiverList) {
							endPoint = getEndPoint(receiverList, sock.getAddress(), sock.getPort());
							if (endPoint == null) {
								// new end point just started
								endPoint = new GUDPEndPoint(sock.getAddress(), sock.getPort());
								endPoint.setBase(0);
								endPoint.setNextseqnum(0);
								endPoint.setLast(0);
								endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
								receiverList.add(endPoint);
								if (debug) {
									System.err.println("ADD RCV ENDPOINT: "
											+ endPoint.getRemoteEndPoint().getAddress() + ":"
											+ endPoint.getRemoteEndPoint().getPort());
								}

								boolean receivePacket = true;
								switch (receiverDrop) {
									case FIRST_BSN:
										// drop BSN for the first packet arrived at the receiver
										if (!endPoint.getDropReceive()) {
											receivePacket = false;
											endPoint.setDropReceive(true);
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											receivePacket = false;
										}
										break;
									case NOTHING:
									case FIRST_DATA:
									case FIRST_FIN:
										// receive the packet normally
										break;
								}

								if (receivePacket) {
									System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno()));
									endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
									sendACK(endPoint, gudppacket);
								} else {
									System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno()) + "\tDROP!");
									// also set the expected seqnum to the BSN that was just dropped
									endPoint.setExpectedseqnum(gudppacket.getSeqno());
								}
							} else if (endPoint.getFinished()) {
								// existing end point that was finished. Reset it!

								boolean receivePacket = true;
								switch (receiverDrop) {
									case FIRST_BSN:
										// assume BSN is always not in the same range as the previous transmission
										if ((gudppacket.getSeqno() <= endPoint.getExpectedseqnum()
												- endPoint.getWindowSize())
												|| (gudppacket.getSeqno() >= endPoint.getExpectedseqnum()
														+ endPoint.getWindowSize())) {
											// New incoming BSN. Reset EndPoint with new seq but drop the BSN packet.
											endPoint.clear();
											endPoint.setFinished(false);
											endPoint.setDropReceive(true);
											endPoint.setExpectedseqnum(gudppacket.getSeqno());
											System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno())
													+ "\tDROP! ALSO RESET END POINT");
											receivePacket = false;
											continue;
										} else {
											// New BSN has probably been dropped once already. So, we don't drop it
											// again.
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											receivePacket = false;
										}
										break;
									case NOTHING:
									case FIRST_DATA:
									case FIRST_FIN:
										// receive the packet normally
										break;
								}

								if (receivePacket) {
									endPoint.clear();
									endPoint.setFinished(false);
									endPoint.setDropReceive(false);
									System.err.println(
											"\t RCV BSN\t" + (gudppacket.getSeqno()) + "\tALSO RESET END POINT");
									endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
									sendACK(endPoint, gudppacket);
								} else {
									System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno())
											+ "\tDROP! endPoint.getFinished()");
								}

							} else {
								// existing end point that is ongoing. Ignore BSN but send old ACK.
								boolean receivePacket = true;
								switch (receiverDrop) {
									case FIRST_BSN:
										if (!endPoint.getDropReceive()) {
											receivePacket = false;
											endPoint.setDropReceive(true);
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											receivePacket = false;
										}
										break;
									case NOTHING:
									case FIRST_DATA:
									case FIRST_FIN:
										// receive the packet normally
										break;
								}

								if (receivePacket) {
									if (gudppacket.getSeqno() == endPoint.getExpectedseqnum()) {
										System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno()));
										endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
										sendACK(endPoint, gudppacket);
									} else {
										System.err.println("\t RCV BSN\t" + (gudppacket.getSeqno())
												+ "\tIGNORE! NOT EXPECTED SEQ");
										// also resend ACK for expectedseqnum
										gudppacket.setSeqno(endPoint.getExpectedseqnum() - 1);
										sendACK(endPoint, gudppacket);
									}
								} else {
									System.err.println(
											"\t RCV BSN\t" + (gudppacket.getSeqno()) + "\tDROP! endPoint not finished");
								}
							}
						} /* synchronized (receiverList) */
						break;

					case GUDPPacket.TYPE_DATA:
						// DATA signifies incoming data from an existing remoteEndPoint
						// Otherwise, ignore the DATA packet
						synchronized (receiverList) {
							endPoint = getEndPoint(receiverList, sock.getAddress(), sock.getPort());
							if (endPoint != null) {

								boolean receivePacket = true;
								switch (receiverDrop) {
									case FIRST_DATA:
										if (!endPoint.getDropReceive()) {
											System.err.println("\t RCV DATA\t" + (gudppacket.getSeqno()) + "\tDROP!");
											endPoint.setDropReceive(true);
											receivePacket = false;
											continue;
										} else {
											// New BSN has probably been dropped once already. So, we don't drop it
											// again.
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											receivePacket = false;
										}
										break;
									case NOTHING:
									case FIRST_BSN:
									case FIRST_FIN:
										// receive the packet normally
										break;
								}

								if (receivePacket) {
									if (gudppacket.getSeqno() == endPoint.getExpectedseqnum()) {
										System.err.println("\t RCV DATA\t" + (gudppacket.getSeqno()));
										endPoint.add(gudppacket);
										endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
										sendACK(endPoint, gudppacket);
										this.receiverList.notify();
									} else {
										System.err.println("\t RCV DATA\t" + (gudppacket.getSeqno())
												+ "\tIGNORE! NOT EXPECTED SEQ");
										// also resend ACK for expectedseqnum
										gudppacket.setSeqno(endPoint.getExpectedseqnum() - 1);
										sendACK(endPoint, gudppacket);
									}
								} else {
									System.err.println("\t RCV DATA\t" + (gudppacket.getSeqno()) + "\tDROP!");
								}
							} else {
								// we ignore a DATA packet arriving from non-existing end point.
								System.err
										.println("\t RCV DATA\t" + (gudppacket.getSeqno()) + "\tIGNORE! NO END POINT");
							}
						} /* synchronized (receiverList) */
						break;

					case GUDPPacket.TYPE_FIN:
						// FIN signifies ending of the ongoing DATA transmission
						// Otherwise, ignore the FIN packet
						// Use last variable to record the end of transmission
						synchronized (receiverList) {
							endPoint = getEndPoint(receiverList, sock.getAddress(), sock.getPort());
							if (endPoint != null) {

								boolean receivePacket = true;
								switch (receiverDrop) {
									case FIRST_FIN:
										if (!endPoint.getDropReceive()) {
											System.err.println("\t RCV FIN\t" + (gudppacket.getSeqno()) + "\tDROP!");
											endPoint.setDropReceive(true);
											receivePacket = false;
											continue;
										} else {
											// New BSN has probably been dropped once already. So, we don't drop it
											// again.
										}
										break;
									case RANDOM:
										if (Math.random() <= endPoint.getChance()) {
											receivePacket = false;
										}
										break;
									case NOTHING:
									case FIRST_BSN:
									case FIRST_DATA:
										// receive the packet normally
										break;
								}

								if (receivePacket) {
									if (gudppacket.getSeqno() == endPoint.getExpectedseqnum()) {
										System.err.println("\t RCV FIN\t" + (gudppacket.getSeqno()));
										endPoint.setExpectedseqnum(gudppacket.getSeqno() + 1);
										endPoint.setFinished(true);
										sendACK(endPoint, gudppacket);
									} else {
										System.err.println("\t RCV FIN\t" + (gudppacket.getSeqno())
												+ "\tIGNORE! NOT EXPECTED SEQ");
										// also resend ACK for expectedseqnum
										gudppacket.setSeqno(endPoint.getExpectedseqnum() - 1);
										sendACK(endPoint, gudppacket);
									}
								} else {
									System.err.println("\t RCV FIN\t" + (gudppacket.getSeqno()) + "\tDROP!");
								}
							} else {
								// we ignore a DATA packet arriving from non-existing end point.
								System.err.println("\t RCV FIN\t" + (gudppacket.getSeqno()) + "\tIGNORE! NO END POINT");
							}
						} /* synchronized (receiverList) */
						break;

				} /* switch */
				// } /* synchronized (receiveList) */

			} catch (IOException e) {
				System.err.println("IOException in ReceiverThread: GUDPPacket.unpack");
				e.printStackTrace();
			}

		} /* while (this.runFlag) */

		System.out.println("ReceiverThread ended");
	} /* public void run() */

	/*
	 * Get the end point from the list. Return null if not found.
	 */
	public GUDPEndPoint getEndPoint(LinkedList<GUDPEndPoint> list, InetAddress addr, int port) {
		synchronized (list) {
			for (int i = 0; i < list.size(); i++) {
				if ((list.get(i).getRemoteEndPoint().getAddress().equals(addr))
						&& (list.get(i).getRemoteEndPoint().getPort() == port)) {
					return list.get(i);
				}
			}
			return null;
		}
	}

	/*
	 * send ACK to the remoteEndPoint
	 */
	public void sendACK(GUDPEndPoint endPoint, GUDPPacket gudppacket) {
		try {
			ByteBuffer ackBuf = ByteBuffer.allocate(GUDPPacket.HEADER_SIZE);
			ackBuf.order(ByteOrder.BIG_ENDIAN);
			GUDPPacket gpack = new GUDPPacket(ackBuf);
			gpack.setSocketAddress(endPoint.getRemoteEndPoint());
			gpack.setVersion(GUDPPacket.GUDP_VERSION);
			gpack.setType(GUDPPacket.TYPE_ACK);
			gpack.setSeqno(gudppacket.getSeqno() + 1);
			byte[] data = new byte[0];
			gpack.setPayloadLength(0);
			gpack.setPayload(data);
			DatagramPacket udpack = gpack.pack();
			if ((receiverDrop == GUDPSocket.drop.FIRST_ACK) && (!endPoint.getDropReceive())) {
				// drop ACK for the first packet sending out by the receiver
				endPoint.setDropReceive(true);
				System.err.println("\tSEND ACK\t" + (gpack.getSeqno()) + "\tDROP!");
			} else {
				this.sock.send(udpack);
				if (debug) {
					System.err.println("\tSEND ACK\t" + (gpack.getSeqno()));
				}
			}
		} catch (IOException e) {
			System.err.println("InterruptedException in SendThread.run()");
			e.printStackTrace();
		}
	} /* public void sendACK */

} /* public class ReceiverThread */
