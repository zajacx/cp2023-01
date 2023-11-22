/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;

public final class StorageSystemFactory {

    public static StorageSystem newSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {

        // Handling all possible exceptions before creating new system:

        if (deviceTotalSlots == null || componentPlacement == null) {
            throw new IllegalArgumentException("Dependencies not defined");
        }

        // All devices and components in the system:
        List<DeviceId> devices = new ArrayList<>(deviceTotalSlots.keySet());
        List<ComponentId> components = new ArrayList<>(componentPlacement.keySet());

        if (devices.isEmpty()) {
            throw new IllegalArgumentException("No devices");
        }

        Map<DeviceId, Integer> deviceFreeSlots = new ConcurrentHashMap<>();

        for (DeviceId device : devices) {
            if (device != null && deviceTotalSlots.get(device) != null) {
                if (deviceTotalSlots.get(device) > 0) {
                    deviceFreeSlots.put(device, deviceTotalSlots.get(device));
                } else {
                    throw new IllegalArgumentException("Device with non-positive capacity");
                }
            } else {
                throw new IllegalArgumentException("Device with non-defined capacity");
            }
        }

        for (ComponentId component : components) {
            DeviceId deviceId = componentPlacement.get(component);
            Integer freeSlots = deviceFreeSlots.get(deviceId);
            deviceFreeSlots.put(deviceId, freeSlots - 1);
        }

        for (DeviceId device : devices) {
            if (deviceFreeSlots.get(device) < 0) {
                throw new IllegalArgumentException(device.toString() + " capacity exceeded");
            }
        }

        return new StorageSystemInstance(deviceFreeSlots, componentPlacement);
    }

}
