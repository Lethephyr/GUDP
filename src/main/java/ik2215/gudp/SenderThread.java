package ik2215.gudp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.LinkedList;

/*
 * SenderThread monitors send queues and sends packets whenever there are packets in the queues
 */
public class SenderThread extends Thread {
	private final DatagramSocket sock;
	private final LinkedList<GUDPEndPoint> senderList;
	private boolean runFlag = true;
	private boolean debug = true;
	private GUDPSocket.drop senderDrop;

	public SenderThread(DatagramSocket sock, LinkedList<GUDPEndPoint> senderList, GUDPSocket.drop senderDrop) {
		this.sock = sock;
		this.senderList = senderList;
		this.senderDrop = senderDrop;
	}

	public void stopSenderThread() {
		this.runFlag = false;
	}

	@Override
	public void run() {
		/*
		 * This is a loop that continously runs until the SenderThread is terminated.
		 * You need to synchronize senderList to avoid race conditions.
		 * wait for application to send packet (senderList is still empty)
		 * when senderList is not empty, begin the loop to iterate through each
		 * remoteEndPoint in the senderList
		 * if there is any packet, send it according to the GBN sender algorithm.
		 * Otherwise, notify other threads, then wait and repeat the process until the
		 * SenderThread is terminated
		 * Moreover, you must also notify other threads before terminating SenderThread
		 *
		 * Before repeating the loop, you may slow down SenderThread by making it sleeps
		 * briefly.
		 * In this case, it is importnat that sleep is done outside the synchronized
		 * block. Otherwise, you may get a deadlock.
		 * The sleep time must also be shorter than sleep time in finish() so that it
		 * can detect when SenderThread terminates.
		 * Otherwise, the main thread may be stuck in wait (and no other threads will
		 * notify it, causing a deadlock).
		 * (The test code put SenderThread to sleep for 50 ms here while finish() sleeps
		 * for 200 ms)
		 *
		 */

		if (senderList.isEmpty()) {
			try {
				synchronized (senderList) {
					senderList.wait();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		while (runFlag) {
			synchronized (senderList) {
				for (GUDPEndPoint endPoint : senderList) {
					FSMSender(endPoint);
					if (endPoint.getFinished() && endPoint.isEmptyQueue()) {
						senderList.remove(endPoint);
					}
				}
				if (senderList.isEmpty()) {
					senderList.notifyAll();
				} else if (allEmptyQueue()) {
					try {
						senderList.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				if (!runFlag) {
					senderList.notifyAll();
				}
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	} /* public void run() */

	public void FSMSender(GUDPEndPoint endPoint) {
		/*
		 * FSM for GBN Sender
		 * INIT: Do nothing. Progress to WAIT.
		 * WAIT: If the queue is not empty, progress to SEND. Otherwise, notify other
		 * threads
		 * SEND: Send packets while the window is not full.
		 * Move to WAIT after iterating through the senderList
		 * RCV: The actual packet reception is done by the ReceiverThread. Here you deal
		 * with the timer.
		 * Also progress to SEND since you probably have more sending window available.
		 * TIMEOUT: Resend packets and restart the timer.
		 * If maximum retransmission, terminate the SendThread, which should
		 * trigger the program to terminate (assuming you monitor it in send and finish
		 * methods).
		 *
		 * NOTE: You do not need to synchronize senderList in this method since it
		 * should have already been synchronized
		 * by other methods that called this method.
		 * /*
		 * DatagramPacket udppacket;
		 * switch (endPoint.getState()) {
		 * case INIT:
		 * case WAIT:
		 * case SEND:
		 * case RCV:
		 * case TIMEOUT:
		 * case DEFAULT:
		 * }
		 */
		DatagramPacket udppacket;
		switch (endPoint.getState()) {
			case INIT:
				endPoint.setState(GUDPEndPoint.endPointState.WAIT);
				break;
			case WAIT:
				if (!endPoint.isEmptyQueue()) {
					endPoint.setState(GUDPEndPoint.endPointState.SEND);
				} else {
					senderList.notifyAll();
				}
				break;
			case SEND:
				while (endPoint.getNextseqnum() < endPoint.getBase() + endPoint.getWindowSize() &&
						endPoint.getNextseqnum() <= endPoint.getLast()) {
					int seqnum = endPoint.getNextseqnum();
					GUDPPacket packet = endPoint.getPacket(seqnum);
					try {
						udppacket = packet.pack();
						sock.send(udppacket);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (endPoint.getBase() == endPoint.getNextseqnum()) {
						endPoint.startTimer();
						// ??? TODO
					}
					endPoint.setNextseqnum(endPoint.getNextseqnum() + 1);
				}
				endPoint.setState(GUDPEndPoint.endPointState.WAIT);
				break;
			case RCV:
				if (endPoint.getNextseqnum() == endPoint.getBase()) {
					endPoint.stopTimer();
					endPoint.setRetry(0);
				} else {
					endPoint.stopTimer();
					endPoint.startTimer();
				}
				endPoint.setState(GUDPEndPoint.endPointState.SEND);
				break;
			case TIMEOUT:
				if (endPoint.getRetry() >= endPoint.getMaxRetry()) {
					stopSenderThread();
					senderList.notifyAll();
					break;
				} else {
					endPoint.startTimer();
					for (int i = endPoint.getBase(); i < endPoint.getNextseqnum(); i++) {
						GUDPPacket packet = endPoint.getPacket(i);
						try {
							udppacket = packet.pack();
							sock.send(udppacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				endPoint.setRetry(endPoint.getRetry() + 1);
				endPoint.setState(GUDPEndPoint.endPointState.SEND);
				break;
			case DEFAULT:
		}

	} /* public void FSMSender(GUDPEndPoint endPoint) */

	private boolean allEmptyQueue() {
		for (GUDPEndPoint endPoint : senderList) {
			if (!endPoint.isEmptyQueue()) {
				return false;
			}
		}
		return true;
	}

	private boolean allFinished() {
		for (GUDPEndPoint endPoint : senderList) {
			if (!endPoint.getFinished()) {
				return false;
			}
		}
		return true;
	}

} /* public class SenderThread */
