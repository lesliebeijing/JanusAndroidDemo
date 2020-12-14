package com.lesliefang.janusdemo2.entity;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by fanglin on 2020/12/14.
 */
public class Room {
    private int id;

    public Room() {

    }

    public Room(int id) {
        this.id = id;
    }

    private Set<Publisher> publishers = new HashSet<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<Publisher> getPublishers() {
        return publishers;
    }

    public void setPublishers(Set<Publisher> publishers) {
        this.publishers = publishers;
    }
}
