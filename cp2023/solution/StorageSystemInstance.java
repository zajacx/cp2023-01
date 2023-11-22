/*
 * Author: Tomasz Zając (tz448580@students.mimuw.edu.pl)
 */

package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.Semaphore;

public class StorageSystemInstance implements StorageSystem {

    // Collection created mainly to handle exceptions:
    private final List<ComponentId> occupied = new LinkedList<>();

    // System status data:
    private final Map<DeviceId, Integer> deviceFreeSlots;
    private final Map<ComponentId, DeviceId> componentPlacement;

    // Additional structures and variables:

    // If a transfer is ready to go, it is here:
    private final Queue<WrappedTransfer> readyTransfers = new LinkedList<>();

    // If a transfer is waiting for a place to be released, it is here:
    private final Queue<WrappedTransfer> waitingTransfers = new LinkedList<>();

    // Mutex:
    private final Semaphore mutex = new Semaphore(1);


    public StorageSystemInstance(
            Map<DeviceId, Integer> deviceFreeSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        this.deviceFreeSlots = deviceFreeSlots;
        this.componentPlacement = componentPlacement;
    }

    // ----------------------------- Public method ------------------------------

    public void execute(ComponentTransfer transfer) throws TransferException {

        ComponentId componentId = transfer.getComponentId();
        DeviceId sourceDeviceId = transfer.getSourceDeviceId();
        DeviceId destinationDeviceId = transfer.getDestinationDeviceId();

        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }

        handleExceptions(transfer);
        WrappedTransfer wrappedTransfer = new WrappedTransfer(transfer);
        occupied.add(componentId);

        // Delete component:
        if (destinationDeviceId == null) {
            transfer.prepare();
            WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
            if (transferToWakeUp != null) {
                wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                wrappedTransfer.wakeTheOtherUp();
            }
            Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
            deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
            componentPlacement.remove(componentId);
            occupied.remove(componentId);
            if (wrappedTransfer.getTransferToWakeUp() != null) {
                wrappedTransfer.wakeTheOtherUp();
            }
            transfer.perform();
            mutex.release();
        } else {
            // Add or move component:
            if (deviceFreeSlots.get(destinationDeviceId) > 0) {
                // There are empty places on a destination device:
                transfer.prepare();
                if (sourceDeviceId != null) {
                    WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
                    if (transferToWakeUp != null) {
                        wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                        wrappedTransfer.wakeTheOtherUp();
                    }
                    Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
                    deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
                }
                Integer destinationFreeSlots = deviceFreeSlots.get(destinationDeviceId);
                deviceFreeSlots.put(destinationDeviceId, destinationFreeSlots - 1);
                componentPlacement.put(componentId, destinationDeviceId);
                occupied.remove(componentId);
                if (wrappedTransfer.getTransferToWakeUp() != null) {
                    wrappedTransfer.wakeTheOtherUp();
                }
                transfer.perform();
                mutex.release();
            } else {
                // There is no empty place on a destination device:
                // Looping over all transfers that are waiting on their semaphores
                // to look for an optional empty place:
                boolean foundQuittingComponentImmediately = false;
                for (WrappedTransfer wrapper : readyTransfers) {
                    if (wrapper.getTransfer().getSourceDeviceId() == destinationDeviceId &&
                            wrapper.getTransferToWakeUp() == null &&
                            !wrapper.equals(wrappedTransfer)) {
                        // Found quitting transfer without any transfer to replace it:
                        foundQuittingComponentImmediately = true;
                        wrapper.setTransferToWakeUp(wrappedTransfer);
                        break;
                    }
                }
                // After forcefully finding a place for a transfer it has to check
                // if any other transfer is waiting for a place on its device:
                if (foundQuittingComponentImmediately) {
                    transfer.prepare();
                    readyTransfers.add(wrappedTransfer);
                    if (sourceDeviceId != null) {
                        WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
                        if (transferToWakeUp != null) {
                            wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                            wrappedTransfer.wakeTheOtherUp();
                        }
                    }
                    mutex.release();
                    wrappedTransfer.goToSleep();
                }
                // No empty places - transfer is added to the queue of all waiting transfers
                else {
                    waitingTransfers.add(wrappedTransfer);
                    // Checking for cycle:
                    Set<DeviceId> visited = new HashSet<>();
                    visited.add(wrappedTransfer.getTransfer().getDestinationDeviceId());
                    ArrayList<WrappedTransfer> cycle = cycleDetector(
                            wrappedTransfer.getTransfer().getSourceDeviceId(), visited);
                    if (cycle != null) {
                        transfer.prepare();
                        // There is a cycle; the only transfer that is not in the cycle array
                        // is the one that has closed the cycle, so there is no need to wake it up.
                        // Waking up every other transfer from the cycle:
                        for (WrappedTransfer wrapper : cycle) {
                            if (!wrapper.equals(wrappedTransfer)) {
                                wrapper.getSemaphore().release();
                            }
                        }
                        readyTransfers.add(wrappedTransfer);
                        if (sourceDeviceId != null) {
                            WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
                            if (transferToWakeUp != null) {
                                wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                                wrappedTransfer.wakeTheOtherUp();
                            }
                        }
                        // Waiting to be moved:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                    }
                    else {
                        // There is no cycle, so the transfer goes to sleep:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                        // If the transfer is waked up, it means that some other transfer
                        // has added it as a transfer to wake up, which means our transfer
                        // has to do the same with another from the queue:
                        transfer.prepare();
                        try {
                            mutex.acquire();
                        } catch (InterruptedException e) {
                            throw new RuntimeException("panic: unexpected thread interruption");
                        }
                        readyTransfers.add(wrappedTransfer);
                        if (sourceDeviceId != null) {
                            WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
                            if (transferToWakeUp != null) {
                                wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                                wrappedTransfer.wakeTheOtherUp();
                            }
                        }
                        // Waiting to be moved:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                    }
                }

                // Here transfer is being waked up for the last time:
                try {
                    mutex.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException("panic: unexpected thread interruption");
                }

                if (wrappedTransfer.getTransferToWakeUp() != null) {
                    wrappedTransfer.wakeTheOtherUp();
                }
                readyTransfers.remove(wrappedTransfer);

                if (sourceDeviceId != null) {
                    Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
                    deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
                }
                Integer destinationFreeSlots = deviceFreeSlots.get(destinationDeviceId);
                deviceFreeSlots.put(destinationDeviceId, destinationFreeSlots - 1);
                componentPlacement.put(componentId, destinationDeviceId);
                occupied.remove(componentId);
                transfer.perform();

                mutex.release();
            }
        }
    }


    // ----------------------------- Private methods -----------------------------

    private void handleExceptions(ComponentTransfer transfer) throws TransferException {
        ComponentId componentId = transfer.getComponentId();
        DeviceId sourceDeviceId = transfer.getSourceDeviceId();
        DeviceId destinationDeviceId = transfer.getDestinationDeviceId();
        // Real-time exception:
        if (occupied.contains(componentId)) {
            throw new ComponentIsBeingOperatedOn(componentId);
        }
        // Parameters-connected exceptions:
        if (sourceDeviceId == null && destinationDeviceId == null) {
            throw new IllegalTransferType(componentId);
        }
        if (sourceDeviceId != null && !deviceFreeSlots.containsKey(sourceDeviceId)) {
            throw new DeviceDoesNotExist(sourceDeviceId);
        }
        if (destinationDeviceId != null && !deviceFreeSlots.containsKey(destinationDeviceId)) {
            throw new DeviceDoesNotExist(destinationDeviceId);
        }
        if (sourceDeviceId != null && sourceDeviceId.equals(destinationDeviceId)) {
            throw new ComponentDoesNotNeedTransfer(componentId, sourceDeviceId);
        }
        if (sourceDeviceId != null && invalidComponent(componentId, sourceDeviceId)) {
            throw new ComponentDoesNotExist(componentId, sourceDeviceId);
        }
        if (sourceDeviceId == null) {
            // (destinationDeviceId != null) is always true here
            for (ComponentId component : componentPlacement.keySet()) {
                if (componentId.equals(component)) {
                    throw new ComponentAlreadyExists(component, componentPlacement.get(component));
                }
            }
        }
    }

    private boolean invalidComponent(ComponentId componentId, DeviceId sourceDeviceId) {
        for (ComponentId component : componentPlacement.keySet()) {
            if (!componentPlacement.containsKey(componentId)) {
                return true;
            }
            if (componentId.equals(component) && !sourceDeviceId.equals(componentPlacement.get(component))) {
                return true;
            }
        }
        return false;
    }

    private WrappedTransfer popFromQueueOfThisDevice(DeviceId deviceId) {
        for (WrappedTransfer wrappedTransfer : waitingTransfers) {
            if (wrappedTransfer.getTransfer().getDestinationDeviceId().equals(deviceId)) {
                waitingTransfers.remove(wrappedTransfer);
                return wrappedTransfer;
            }
        }
        return null;
    }

    private ArrayList<WrappedTransfer> cycleDetector(DeviceId current, Set<DeviceId> visited) {
        if (current == null) {
            return null;
        }
        visited.add(current);
        ArrayList<WrappedTransfer> cycle = new ArrayList<>();
        if (checkIfSomeoneIsWaitingFor(current)) {
            Queue<WrappedTransfer> transfers = makeListOfTransfersTo(current);
            for (WrappedTransfer wrappedTransfer : transfers) {
                if (visited.contains(wrappedTransfer.getTransfer().getSourceDeviceId())) {
                    cycle.add(wrappedTransfer);
                    return cycle;
                } else {
                    ArrayList<WrappedTransfer> subcycle = cycleDetector(
                            wrappedTransfer.getTransfer().getSourceDeviceId(), visited);
                    if (subcycle != null) {
                        cycle.add(wrappedTransfer);
                        cycle.addAll(subcycle);
                        return cycle;
                    }
                }
            }
        }
        return null;
    }

    private boolean checkIfSomeoneIsWaitingFor(DeviceId deviceId) {
        for (WrappedTransfer wrappedTransfer : waitingTransfers) {
            if (wrappedTransfer.getTransfer().getDestinationDeviceId().equals(deviceId)) {
                return true;
            }
        }
        return false;
    }

    private Queue<WrappedTransfer> makeListOfTransfersTo(DeviceId deviceId) {
        Queue<WrappedTransfer> result = new LinkedList<>();
        for (WrappedTransfer wrappedTransfer : waitingTransfers) {
            if (wrappedTransfer.getTransfer().getDestinationDeviceId().equals(deviceId)) {
                result.add(wrappedTransfer);
            }
        }
        return result;
    }

}