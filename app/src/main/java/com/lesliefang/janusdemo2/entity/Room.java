package com.lesliefang.janusdemo2.entity;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
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

    public void addPublisher(Publisher publisher) {
        Iterator<Publisher> it = publishers.iterator();
        boolean found = false;
        while (it.hasNext()) {
            Publisher next = it.next();
            if (next.getId().equals(publisher.getId())) {
                found = true;
                break;
            }
        }
        if (!found) {
            publishers.add(publisher);
        }
    }

    public Publisher findPublisherById(BigInteger id) {
        for (Publisher next : publishers) {
            if (next.getId().equals(id)) {
                return next;
            }
        }
        return null;
    }

    public void removePublisherById(BigInteger id) {
        Iterator<Publisher> it = publishers.iterator();
        while (it.hasNext()) {
            Publisher next = it.next();
            if (next.getId().equals(id)) {
                it.remove();
            }
        }
    }
}
