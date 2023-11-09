package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.TransferException;

import java.util.List;
import java.util.Map;

public class StorageSystemInstance implements StorageSystem {

    private final List<DeviceId> devices;
    private final List<ComponentId> components;
    private final Map<DeviceId, Integer> deviceTotalSlots;
    private final Map<ComponentId, DeviceId> componentPlacement;

    public StorageSystemInstance(
            List<DeviceId> devices,
            List<ComponentId> components,
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        this.devices = devices;
        this.components = components;
        this.deviceTotalSlots = deviceTotalSlots;
        this.componentPlacement = componentPlacement;
    }

    public void execute(ComponentTransfer transfer) throws TransferException {
        // Tutaj wszystkie wątki
    }

}
