package cp2023.solution;

import cp2023.base.ComponentTransfer;

import java.util.concurrent.Semaphore;

public class WrappedTransfer {
    private final ComponentTransfer transfer;
    private final Semaphore semaphore = new Semaphore(0);
    private WrappedTransfer transferToWakeUp;

    public WrappedTransfer(ComponentTransfer transfer) {
        this.transfer = transfer;
    }

    // ----------------- Getters & setters -----------------

    public Semaphore getSemaphore() {
        return semaphore;
    }

    public ComponentTransfer getTransfer() {
        return transfer;
    }

    public WrappedTransfer getTransferToWakeUp() {
        return transferToWakeUp;
    }

    public void setTransferToWakeUp(WrappedTransfer other) {
        this.transferToWakeUp = other;
    }

    // ---------------- Semaphore operations ----------------

    public void goToSleep() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void wakeTheOtherUp() {
        transferToWakeUp.getSemaphore().release();
    }
}
