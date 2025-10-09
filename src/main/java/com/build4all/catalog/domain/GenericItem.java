package com.build4all.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "items_base")
public class GenericItem extends Item {
 // no extra fields
}
