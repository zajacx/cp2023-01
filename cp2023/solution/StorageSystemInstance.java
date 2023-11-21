package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class StorageSystemInstance implements StorageSystem {

    // Collections created mainly to handle exceptions:
    private final List<DeviceId> devices;       // też rozważyć, czy nie lepiej byłoby się odwoływać do map
    private final List<ComponentId> components;
    private final Map<ComponentId, WrappedTransfer> currentTransfers = new ConcurrentHashMap<>();

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
            List<DeviceId> devices,
            List<ComponentId> components,
            Map<DeviceId, Integer> deviceFreeSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        this.devices = devices;
        this.components = components;
        this.deviceFreeSlots = deviceFreeSlots;
        this.componentPlacement = componentPlacement;
    }

    // ----------------------------- Public methods ------------------------------

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
        currentTransfers.put(componentId, wrappedTransfer);

        // TRANSFER:

        // USUWANIE:
        if (destinationDeviceId == null) {
            transfer.prepare();
            WrappedTransfer transferToWakeUp = popFromQueueOfThisDevice(sourceDeviceId);
            if (transferToWakeUp != null) {
                wrappedTransfer.setTransferToWakeUp(transferToWakeUp);
                System.out.println("Transfer " + componentId.toString() + " dodał " +
                        wrappedTransfer.getTransferToWakeUp().getTransfer().getComponentId().toString() +
                        " jako swojego następcę");
                wrappedTransfer.wakeTheOtherUp();
                System.out.println("Obudzono " +
                        wrappedTransfer.getTransferToWakeUp().getTransfer().getComponentId().toString());
            }
            Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
            deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
            componentPlacement.remove(componentId);
            currentTransfers.remove(componentId);
            if (wrappedTransfer.getTransferToWakeUp() != null) {
                wrappedTransfer.wakeTheOtherUp();
            }
            transfer.perform();
            mutex.release();
        } else {
            // DODAWANIE LUB PRZENOSZENIE:
            if (deviceFreeSlots.get(destinationDeviceId) > 0) {
                // SĄ WOLNE MIEJSCA:
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
                currentTransfers.remove(componentId);
                if (wrappedTransfer.getTransferToWakeUp() != null) {
                    wrappedTransfer.wakeTheOtherUp();
                }
                transfer.perform();
                mutex.release();
            } else {
                // NIE MA WOLNYCH MIEJSC:
                // Przechodzimy po wszystkich oczekujących transferach, bo być może
                // ktoś wisi na swoim semaforze, a będzie za chwilę zwalniał miejsce:
                boolean foundQuittingComponentImmediately = false;
                for (WrappedTransfer wrapper : readyTransfers) {
                    if (wrapper.getTransfer().getSourceDeviceId() == destinationDeviceId &&
                            wrapper.getTransferToWakeUp() == null &&
                            !wrapper.equals(wrappedTransfer)) {
                        // Znaleźliśmy wychodzący transfer, który jeszcze nie ma następcy:
                        foundQuittingComponentImmediately = true;
                        wrapper.setTransferToWakeUp(wrappedTransfer);
                        System.out.println("Znaleziono od razu miejsce dla " + componentId.toString() + " na urządzeniu "
                                + destinationDeviceId + " w miejsce " + wrapper.getTransfer().getComponentId().toString());
                        break;
                    }
                }
                // Jeśli "siłowo" znaleźliśmy miejsce dla transferu (przez przeszukiwanie)
                // od razu szukamy dla niego następcy w kolejce urządzenia i tego następcę
                // budzimy, bo transfer, za który wchodzimy, zaraz się wykona:
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
                else {
                    // Transfer dołącza do kolejki oczekująćych:
                    System.out.println("Dodano " + componentId.toString() + " do kolejki oczekujących na miejsce");
                    waitingTransfers.add(wrappedTransfer);
                    // Sprawdzamy, czy w waitingTransfer powstał cykl:
                    WrappedTransfer cycleBeginning = cycleDetector(waitingTransfers);
                    if (cycleBeginning != null) {
                        // cycleBeginning to najdłużej czekający transfer w całym cyklu.
                        // Budzimy go siłowo, a następnie on kaskadowo budzi pozostałych:
                        cycleBeginning.getSemaphore().release();
                        waitingTransfers.remove(cycleBeginning);
                        // W międzyczasie nasz transfer idzie spać:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                        // Budzenie domykająćego:
                        System.out.println(componentId + ": jestem domykającym w cyklu i zostałem obudzony");
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
                        // Oczekiwanie na przenoszenie:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                    }
                    else {
                        // Nie powstał cykl, więc transfer zasypia:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                        // Jeżeli transfer jest budzony, to znaczy, że ktoś dodał go jako
                        // swojego następcę, więc teraz transfer musi zrobić to samo:
                        System.out.println(componentId + ": zostałem obudzony");
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
                        // Oczekiwanie na przenoszenie:
                        mutex.release();
                        wrappedTransfer.goToSleep();
                    }
                }

                // W tym miejscu transfer jest budzony po to, aby się ostatecznie wykonać:
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
                currentTransfers.remove(componentId);
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
        if (currentTransfers.get(componentId) != null) {
            throw new ComponentIsBeingOperatedOn(componentId);
        }
        // Parameters-connected exceptions:
        if (sourceDeviceId == null && destinationDeviceId == null) {
            throw new IllegalTransferType(componentId);
        }
        if (sourceDeviceId != null && !devices.contains(sourceDeviceId)) {
            throw new DeviceDoesNotExist(sourceDeviceId);
        }
        if (destinationDeviceId != null && !devices.contains(destinationDeviceId)) {
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
            for (ComponentId component : components) {
                if (componentId.equals(component)) {
                    throw new ComponentAlreadyExists(component, componentPlacement.get(component));
                }
            }
        }
    }

    private boolean invalidComponent(ComponentId componentId, DeviceId sourceDeviceId) {
        for (ComponentId component : components) {
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

    // Wykrywa cykl i zwraca najdłużej czekający transfer w tym cyklu.
    private WrappedTransfer cycleDetector(Queue<WrappedTransfer> waitingTransfers) {
        Set<WrappedTransfer> visited = new HashSet<>();
        for (WrappedTransfer transfer : waitingTransfers) {
            if (!visited.contains(transfer)) {
                WrappedTransfer result = detectCycleDFS(transfer, visited);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private WrappedTransfer detectCycleDFS(WrappedTransfer current, Set<WrappedTransfer> visited) {
        if (current == null || visited.contains(current)) {
            return null;    // brak cyklu lub już jest odwiedzony
        }
        visited.add(current);
        DeviceId currentDestination = current.getTransfer().getDestinationDeviceId();
        // Iterujemy przez wszystkie transfery:
        for (WrappedTransfer next : waitingTransfers) {
            if (next != current) {
                DeviceId nextSourceDeviceId = next.getTransfer().getSourceDeviceId();
                if (nextSourceDeviceId != null && nextSourceDeviceId.equals(currentDestination)) {
                    if (visited.contains(next)) {
                        return current; // znaleziono cykl
                    } else {
                        WrappedTransfer result = detectCycleDFS(next, visited);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }
        return null; // brak cyklu
    }


    // others...


}
