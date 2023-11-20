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
    private final Map<DeviceId, Integer> deviceTotalSlots;
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

        mutex.release();

        // TRANSFER:

        if (destinationDeviceId == null) {
            try {
                mutex.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }
            Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
            deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
            componentPlacement.remove(componentId);
            currentTransfers.remove(componentId);
            // tu gdzieś wywołać prepare i perform
            mutex.release();
        } else {
            // Następuje dodawanie lub przenoszenie
            try {
                mutex.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }
            if (deviceFreeSlots.get(destinationDeviceId) > 0) {
                // SĄ WOLNE MIEJSCA:
                Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
                deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
                Integer destinationFreeSlots = deviceFreeSlots.get(destinationDeviceId);
                deviceFreeSlots.put(destinationDeviceId, destinationFreeSlots - 1);
                componentPlacement.put(componentId, destinationDeviceId);
                currentTransfers.remove(componentId);
                // tu gdzieś wywołać prepare i perform
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
                        break;
                    }
                }
                // Jeśli "siłowo" znaleźliśmy miejsce dla transferu (przez przeszukiwanie)
                // od razu szukamy dla niego następcy w kolejce urządzenia i tego następcę
                // budzimy, bo transfer, za który wchodzimy, zaraz się wykona:
                if (foundQuittingComponentImmediately) {
                    waitingTransfers.put(componentId, wrappedTransfer);
                    Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
                    if (sourceDeviceQueue.peek() != null) {
                        wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
                        wrappedTransfer.wakeTheOtherUp();
                    }
                    mutex.release();
                    wrappedTransfer.goToSleep();
                    // Jeżeli transfer jest budzony, to znaczy, że jego poprzednik na
                    // docelowym urządzeniu już się przenosi:
                    // Przenoszenie:
                    // TODO: kawałki kodu na przenoszenie (poza ifem?)
                }
                else {
                    // Transfer dołącza do kolejki oczekująćych na dane urządzenie:
                    Queue<WrappedTransfer> destinationDeviceQueue = queues.get(destinationDeviceId);
                    destinationDeviceQueue.add(wrappedTransfer);
                    waitingTransfers.put(componentId, wrappedTransfer);
                    mutex.release();
                    wrappedTransfer.goToSleep();
                    // Jeżeli transfer jest budzony, to znaczy, że ktoś dodał go jako
                    // swojego następcę, więc teraz transfer musi zrobić to samo:
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("panic: unexpected thread interruption");
                    }
                    Queue<WrappedTransfer> sourceDeviceQueue = queues.get(sourceDeviceId);
                    if (sourceDeviceQueue.peek() != null) {
                        wrappedTransfer.setTransferToWakeUp(sourceDeviceQueue.poll());
                        wrappedTransfer.wakeTheOtherUp();
                    }
                    // Oczekiwanie na przenoszenie:
                    mutex.release();
                    wrappedTransfer.goToSleep();
                    // Budzenie po raz drugi - przenoszenie:
                    // TODO: kawałki kodu na przenoszenie (poza ifem?)
                }

                // Przenoszenie:
                try {
                    mutex.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException("panic: unexpected thread interruption");
                }

                // PERFORM

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
            if (componentId.equals(component) && sourceDeviceId != componentPlacement.get(component)) {
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
