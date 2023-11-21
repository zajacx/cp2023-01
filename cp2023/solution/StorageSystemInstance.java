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
    private final List<DeviceId> devices;
    private final List<ComponentId> components;

    // System status data:
    private final Map<DeviceId, Integer> deviceTotalSlots; // porządnie rozważyć sens tego XD
    private final Map<DeviceId, Integer> deviceFreeSlots;
    private final Map<ComponentId, DeviceId> componentPlacement;

    // Additional structures and variables:

    // Map of components and their current transfers:
    private final Map<ComponentId, WrappedTransfer> currentTransfers = new ConcurrentHashMap<>();

    // If a transfer is waiting, it's here:
    private final Map<ComponentId, WrappedTransfer> waitingTransfers = new ConcurrentHashMap<>();

    // Map of devices and their queues:
    private final Map<DeviceId, Queue<WrappedTransfer>> queues = new ConcurrentHashMap<>();

    // Mutex:
    private final Semaphore mutex = new Semaphore(1);


    public StorageSystemInstance(
            List<DeviceId> devices,
            List<ComponentId> components,
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<DeviceId, Integer> deviceFreeSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        this.devices = devices;
        this.components = components;
        this.deviceTotalSlots = deviceTotalSlots;
        this.deviceFreeSlots = deviceFreeSlots;
        this.componentPlacement = componentPlacement;
        // Additional structures:
        initializeQueues();
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
            Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
            if (sourceDeviceQueue.peek() != null) {
                wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
                System.out.println("Transfer " + componentId.toString() + " dodał " + wrappedTransfer.getTransferToWakeUp().getTransfer().getComponentId().toString() +
                        " jako swojego następcę");
                wrappedTransfer.wakeTheOtherUp();
                System.out.println("Obudzono " + wrappedTransfer.getTransferToWakeUp().getTransfer().getComponentId().toString());
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
                    Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
                    if (sourceDeviceQueue.peek() != null) {
                        wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
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
                for (WrappedTransfer wrapper : waitingTransfers.values()) {
                    if (wrapper.getTransfer().getSourceDeviceId() == destinationDeviceId &&
                            wrapper.getTransferToWakeUp() == null &&
                            !wrapper.equals(wrappedTransfer)) {
                        // Znaleźliśmy wychodzący transfer, który jeszcze nie ma następcy:
                        foundQuittingComponentImmediately = true;
                        wrapper.setTransferToWakeUp(wrappedTransfer);
                        System.out.println("Znaleziono od razu miejsce dla " + componentId.toString() + " na urządzeniu "
                                + destinationDeviceId.toString() + " w miejsce " + wrapper.getTransfer().getComponentId().toString());
                        break;
                    }
                }
                // Jeśli "siłowo" znaleźliśmy miejsce dla transferu (przez przeszukiwanie)
                // od razu szukamy dla niego następcy w kolejce urządzenia i tego następcę
                // budzimy, bo transfer, za który wchodzimy, zaraz się wykona:
                if (foundQuittingComponentImmediately) {
                    transfer.prepare();
                    waitingTransfers.put(componentId, wrappedTransfer);
                    if (sourceDeviceId != null) {
                        Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
                        if (sourceDeviceQueue.peek() != null) {
                            wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
                            wrappedTransfer.wakeTheOtherUp();
                        }
                    }
                    mutex.release();
                    wrappedTransfer.goToSleep();
                }
                else {
                    // Transfer dołącza do kolejki oczekująćych na dane urządzenie:
                    Queue<WrappedTransfer> destinationDeviceQueue = queues.get(destinationDeviceId);
                    System.out.println("Dodano " + componentId.toString() + " do kolejki na urządzeniu " + destinationDeviceId.toString());
                    destinationDeviceQueue.add(wrappedTransfer);
                    mutex.release();
                    wrappedTransfer.goToSleep();
                    // Jeżeli transfer jest budzony, to znaczy, że ktoś dodał go jako
                    // swojego następcę, więc teraz transfer musi zrobić to samo:
                    System.out.println(componentId.toString() + ": zostałem obudzony");
                    transfer.prepare();
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("panic: unexpected thread interruption");
                    }
                    waitingTransfers.put(componentId, wrappedTransfer);
                    if (sourceDeviceId != null) {
                        Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
                        if (sourceDeviceQueue.peek() != null) {
                            wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
                            wrappedTransfer.wakeTheOtherUp();
                        }
                    }
                    // Oczekiwanie na przenoszenie:
                    mutex.release();
                    wrappedTransfer.goToSleep();
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
                waitingTransfers.remove(componentId);

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

    private void initializeQueues() {
        for (DeviceId device : devices) {
            queues.put(device, new LinkedList<>());
        }
    }

    // others...


}
