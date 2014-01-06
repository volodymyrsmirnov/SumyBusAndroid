package com.mindcollapse.sumybus;

import java.io.Serializable;

public class Route implements Serializable {
    private int id;
    private String name;
    private String description;

    public Route(int id, String name, String description) {
        super();

        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (id: %d)", this.name, this.description, this.id);
    }
}
