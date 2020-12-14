package com.lesliefang.janusdemo2.entity;

import java.util.Objects;

/**
 * Created by fanglin on 2020/12/14.
 */
public class Publisher {
    private Long id;
    private String display;

    public Publisher() {

    }

    public Publisher(Long id, String display) {
        this.id = id;
        this.display = display;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Publisher publisher = (Publisher) o;
        return id.equals(publisher.id) &&
                display.equals(publisher.display);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, display);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    @Override
    public String toString() {
        return "Publisher{" +
                "id=" + id +
                ", display='" + display + '\'' +
                '}';
    }
}
