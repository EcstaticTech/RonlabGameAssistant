package com.ronlab.rga.gui;

import java.util.List;

public class MenuDefinition {

    private final String name;
    private final String title;
    private final int size;
    private final List<MenuItemDefinition> items;

    public MenuDefinition(String name, String title, int size, List<MenuItemDefinition> items) {
        this.name = name;
        this.title = title;
        this.size = size;
        this.items = items;
    }

    public String getName() { return name; }
    public String getTitle() { return title; }
    public int getSize() { return size; }
    public List<MenuItemDefinition> getItems() { return items; }
}
