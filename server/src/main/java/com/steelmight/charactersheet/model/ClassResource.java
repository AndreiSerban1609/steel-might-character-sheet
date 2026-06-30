package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class ClassResource {

    private String resourceType;
    private int currentResource;
    private int maxResource;

    protected ClassResource() {}

    public ClassResource(String resourceType, int currentResource, int maxResource) {
        this.resourceType = resourceType;
        this.currentResource = currentResource;
        this.maxResource = maxResource;
    }

    public String getType() { return resourceType; }
    public int getCurrent() { return currentResource; }
    public int getMax() { return maxResource; }

    public void setCurrent(int value) { this.currentResource = value; }
    public void setMax(int value) { this.maxResource = value; }
}
