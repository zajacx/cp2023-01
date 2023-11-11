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
    private final Map<ComponentId, DeviceId> componentPlacement;

    // Additional structures and variables:

    // Map of devices and their contents placed in distinguishable slots:
    private final Map<DeviceId, ComponentId[]> deviceContents = new ConcurrentHashMap<>();

    // Map of components and their current transfers
    // (currentTransfer.values() is a set of all current transfers):
    private final Map<ComponentId, ComponentTransfer> currentTransfer = new ConcurrentHashMap<>();

    // Semaphores for each component:
    private final Map<ComponentId, Semaphore> semaphores = new ConcurrentHashMap<>();

    // Mutex:
    private final Semaphore mutex = new Semaphore(1);




    public StorageSystemInstance(
            List<DeviceId> devices,
            List<ComponentId> components,
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        this.devices = devices;
        this.components = components;
        this.deviceTotalSlots = deviceTotalSlots;
        this.componentPlacement = componentPlacement;
        // Additional structures:
        initializeContents(deviceContents);
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

        // EXCEPTIONS:
        if (sourceDeviceId == null && destinationDeviceId == null) {
            throw new IllegalTransferType(componentId);
        }
        if (sourceDeviceId != null && !devices.contains(sourceDeviceId)) {
            throw new DeviceDoesNotExist(sourceDeviceId);
        }
        if (destinationDeviceId != null && !devices.contains(destinationDeviceId)) {
            throw new DeviceDoesNotExist(destinationDeviceId);
        }
        if (sourceDeviceId == null) {
            // (destinationDeviceId != null) is always true here
            for (ComponentId component : components) {
                if (componentId.equals(component)) {
                    throw new ComponentAlreadyExists(component, componentPlacement.get(component));
                }
            }
        }
        if (sourceDeviceId != null && sourceDeviceId.equals(destinationDeviceId)) {
            throw new ComponentDoesNotNeedTransfer(componentId, sourceDeviceId);
        }
        if (currentTransfer.get(componentId) != null) {
            throw new ComponentIsBeingOperatedOn(componentId);
        }
        if (sourceDeviceId != null && invalidComponent(componentId, sourceDeviceId)) {
            throw new ComponentDoesNotExist(componentId, sourceDeviceId);
        }

        mutex.release();

    }


    // ----------------------------- Private methods -----------------------------

    private void initializeContents(Map<DeviceId, ComponentId[]> deviceContents) {
        Map<DeviceId, List<ComponentId>> help = new HashMap<>();

        for (DeviceId deviceId : devices) {
            help.put(deviceId, new LinkedList<>());
        }
        for (ComponentId componentId : components) {
            DeviceId destination = componentPlacement.get(componentId);
            List<ComponentId> list = help.get(destination);
            list.add(componentId);
        }
        for (DeviceId deviceId : devices) {
            Integer capacity = deviceTotalSlots.get(deviceId);
            ComponentId[] slots = new ComponentId[capacity];
            List<ComponentId> list = help.get(deviceId);
            list.toArray(slots);
            deviceContents.put(deviceId, slots);
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

    // others...


}
