package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.TransferException;

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

    // Map of components and their current transfers (initially nulled):
    private final Map<ComponentId, ComponentTransfer> currentTransfer = new ConcurrentHashMap<>();

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

    public void execute(ComponentTransfer transfer) throws TransferException {
        // Tutaj wszystkie wątki
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

    // others...


}
