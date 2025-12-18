package com.build4all.features.ecommerce.domain;

/*
🟢 SIMPLE

A normal product with:

One price

One stock quantity

Example:

“USB Cable” – price 10$, no sizes/colors.

Used when you don’t need variations.

🟡 VARIABLE

Product that has variations (same base product, different options).

Example:

T-shirt with:

Sizes: S, M, L

Colors: Red, Blue, Black

Each variation can have:

Its own price

Its own stock

In DB, you usually have:

Parent product with type = VARIABLE

Child rows for each variation.

🔵 GROUPED

A bundle / set of products sold together.

Example:

“Back to School Pack”:

Notebook + Pen + Backpack

You group multiple existing products into one grouped product.

Price can be:

Sum of items

Or special bundle price (depends on your design).
 */
public enum ProductType {
    SIMPLE,    // normal product, one price
    VARIABLE,  // later: variations (size/color)
    GROUPED,   // group/bundle of items
    EXTERNAL   // affiliate / external link
}
