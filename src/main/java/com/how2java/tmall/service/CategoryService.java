package com.how2java.tmall.service;

import com.how2java.tmall.dao.CategoryDAO;
import com.how2java.tmall.pojo.Category;
import com.how2java.tmall.pojo.Product;
import com.how2java.tmall.util.Page4Navigator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@CacheConfig(cacheNames = "categories")
public class CategoryService {
    @Autowired
    CategoryDAO categoryDAO;

    @Cacheable(key = "'categories-page-'+#p0+'-'+#p1")
    public Page4Navigator<Category> list(int start, int size, int navigatePages) {
        Sort sort = new Sort(Sort.Direction.DESC, "id");
        Pageable pageable = new PageRequest(start, size, sort);
        Page pageFromJPA = categoryDAO.findAll(pageable);
        return new Page4Navigator<>(pageFromJPA, navigatePages);
    }

    @Cacheable(key = "'categories-all'")
    public List<Category> list() {
        Sort sort = new Sort(Sort.Direction.DESC, "id");
        return categoryDAO.findAll(sort);
    }

    @CacheEvict(allEntries = true)
    public void add(Category bean) {
        categoryDAO.save(bean);
    }

    @CacheEvict(allEntries = true)
    public void delete(int id) {
        categoryDAO.delete(id);
    }

    @Cacheable(key = "'categories-one-' + #p0")
    public Category get(int id) {
        return categoryDAO.findOne(id);
    }

    @CacheEvict(allEntries = true)
    public void update(Category bean) {
        categoryDAO.save(bean);
    }

    public void removeCategoryFromProduct(List<Category> categories) {
        for (Category category : categories) {
            removeCategoryFromProduct(category);
        }
    }

    public void removeCategoryFromProduct(Category category) {
        List<Product> products = category.getProducts();
        if (products != null) {
            for (Product product : products) {
                product.setCategory(null);
            }
        }

        List<List<Product>> productsByRow = category.getProductsByRow();
        if (productsByRow != null) {
            for (List<Product> productList : productsByRow) {
                for (Product product : productList) {
                    product.setCategory(null);
                }
            }
        }
    }
}
