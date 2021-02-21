package com.how2java.tmall.comparator;

import com.how2java.tmall.pojo.Product;

import java.util.Comparator;

public class ProductReviewComparator implements Comparator<Product> {
    @Override
    public int compare(Product product0, Product product1) {
        return product1.getReviewCount() - product0.getReviewCount();
    }
}
