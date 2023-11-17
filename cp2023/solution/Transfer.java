package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.Map;
import java.util.Queue;

public class Transfer implements ComponentTransfer {
    private final ComponentId componentId;
    private final DeviceId sourceDeviceId;
    private final DeviceId destinationDeviceId;

    public Transfer(
            ComponentId componentId,
            DeviceId sourceDeviceId,
            DeviceId destinationDeviceId) {
        this.componentId = componentId;
        this.sourceDeviceId = sourceDeviceId;
        this.destinationDeviceId = destinationDeviceId;
    }

    public ComponentId getComponentId() {
        return componentId;
    }

    public DeviceId getSourceDeviceId() {
        return sourceDeviceId;
    }

    public DeviceId getDestinationDeviceId() {
        return destinationDeviceId;
    }

    public void prepare() {
        // if(docelowe != null) {
        //   dodaj do kolejki na urządzeniu docelowym
        // }
    }

    public void perform() {
        // P(mutex);
        // zmień pozycję elementu w componentPlacement;
        // V(mutex);
    }

}
