package pl.ts;

import java.util.List;

public class BoatsApiFallback implements BoatsApi {

    static final String BOAT_NAME = "The life raft";

    @Override
    public List<Boat> getBoats() {
        return List.of();
    }

    @Override
    public Boat getBoat(Long id) {
        return new Boat(BOAT_NAME);
    }
}
