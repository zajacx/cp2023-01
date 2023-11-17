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

    private final List<DeviceId> devices;
    private final List<ComponentId> components;
    private final Map<DeviceId, Integer> deviceTotalSlots;
    private final Map<DeviceId, Integer> deviceFreeSlots;
    private final Map<ComponentId, DeviceId> componentPlacement;

    // Additional structures and variables:

    // Map of components and their current transfers:
    private final Map<ComponentId, WrappedTransfer> currentTransfers = new ConcurrentHashMap<>();

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

    /**
     * Algorytm realizacji transferu:
     * jeśli urządzenie docelowe == null : zacznij od razu
     * wpp:
     * jeśli na urządzeniu docelowym istnieje miejsce, które nie zostało przez
     * nikogo zarezerwowane : zacznij transfer
     * wpp:
     * jeśli z urządzenia docelowego coś będzie transferowane i transfer
     * jest zatwierdzony (faza prepare zakończona) : zacznij transfer
     * wpp:
     * sprawdź, czy w systemie istnieje cykl
     * -> rozwiąż cykl zasuwką (?)
     */
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
            transfer.prepare();
            transfer.perform();
        } else {
            // następuje dodawanie lub przenoszenie
            // P(mutex);
            try {
                mutex.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }

            if (deviceFreeSlots.get(destinationDeviceId) > 0) {
                // Są wolne miejsca:
                Integer sourceFreeSlots = deviceFreeSlots.get(sourceDeviceId);
                deviceFreeSlots.put(sourceDeviceId, sourceFreeSlots + 1);
                Integer destinationFreeSlots = deviceFreeSlots.get(destinationDeviceId);
                deviceFreeSlots.put(destinationDeviceId, destinationFreeSlots - 1);
                componentPlacement.put(componentId, destinationDeviceId);
                currentTransfers.remove(componentId);
                mutex.release();
            } else {
                // Nie ma wolnych miejsc - włączamy oczekiwanie:

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
