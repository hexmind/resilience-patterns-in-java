package pl.ts;

import java.util.Objects;

public class Boat {

    private String name;

    public Boat() {
    }

    public Boat(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Boat boat = (Boat) o;
        return Objects.equals(name, boat.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
