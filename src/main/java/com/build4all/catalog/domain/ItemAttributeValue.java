package com.build4all.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "item_attribute_values")
public class ItemAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_attribute_value_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    private ItemAttribute attribute;

    @Column(name = "value", nullable = false)
    private String value;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public ItemAttribute getAttribute() { return attribute; }
    public void setAttribute(ItemAttribute attribute) { this.attribute = attribute; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}