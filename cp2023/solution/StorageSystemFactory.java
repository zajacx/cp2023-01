/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;

/*
 * Solution - pakiet na rozwiązanie.
 * Nie tworzyć podpakietów!
 * Implementacja nie może tworzyć wątków.
 */

public final class StorageSystemFactory {

    /*
     * Instancjonowanie implementacji systemu.
     * Implementacja nie może tworzyć wątków ani wypisywać niczego na wyjścia System.out i System.err.
     * Wiele obiektów systemu powinno być w stanie działać razem.
     * (trzeba kontrolować czy używane urządzenia są w wielu systemach?)
     *
     *
     * Jeśli konfiguracja systemu dostarczona jako argumenty tej
     * metody jest niepoprawna (np. jakiś komponent jest przypisany do urządzenia bez podanej pojemności
     * lub liczba komponentów przypisanych do jakiegoś urządzenia przekracza jego pojemność), to metoda
     * powinna podnieść wyjątek java.lang.IllegalArgumentException z odpowiednim komunikatem tekstowym.
     */

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

        Map<DeviceId, Integer> deviceOccupiedSlots = new HashMap<>(devices.size());

        for (DeviceId device : devices) {
            if (deviceTotalSlots.get(device) != null) {
                deviceOccupiedSlots.put(device, 0);
            } else {
                throw new IllegalArgumentException("Device with non-defined capacity");
            }
        }

        for (ComponentId component : components) {
            DeviceId deviceId = componentPlacement.get(component);
            Integer occupiedSlots = deviceOccupiedSlots.get(deviceId);
            deviceOccupiedSlots.put(deviceId, occupiedSlots + 1);
        }

        for (DeviceId device : devices) {
            if (deviceOccupiedSlots.get(device) > deviceTotalSlots.get(device)) {
                throw new IllegalArgumentException(device.toString() + " capacity exceeded");
            }
        }

        return new StorageSystemInstance(devices, components, deviceTotalSlots, componentPlacement);
    }

}
