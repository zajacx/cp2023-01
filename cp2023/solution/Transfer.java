/*
 * Author: Tomasz Zając (tz448580@students.mimuw.edu.pl)
 */

package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

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

    }

    public void perform() {

    }
}
