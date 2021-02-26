package com.how2java.tmall.web;

import com.how2java.tmall.comparator.*;
import com.how2java.tmall.dao.OrderDAO;
import com.how2java.tmall.pojo.*;
import com.how2java.tmall.service.*;
import com.how2java.tmall.util.Result;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
public class ForeRESTController {
    @Autowired
    CategoryService categoryService;
    @Autowired
    ProductService productService;
    @Autowired
    UserService userService;
    @Autowired
    ProductImageService productImageService;
    @Autowired
    PropertyValueService propertyValueService;
    @Autowired
    ReviewService reviewService;
    @Autowired
    OrderItemService orderItemService;
    @Autowired
    OrderDAO orderDAO;
    @Autowired
    OrderService orderService;

    @GetMapping("/forehome")
    public Object home() {
        List<Category> categories = categoryService.list();
        productService.fill(categories);
        productService.fillByRow(categories);
        categoryService.removeCategoryFromProduct(categories);
        return categories;
    }

    @PostMapping("/foreregister")
    public Object register(@RequestBody User user) {
        String name = user.getName();
        String password = user.getPassword();
        name = HtmlUtils.htmlEscape(name);
        user.setName(name);

        boolean exist = userService.isExist(name);

        if (exist) {
            String message = "用户名已经被使用,不能使用";
            return Result.fail(message);
        }

        String salt = new SecureRandomNumberGenerator().nextBytes().toString();
        int times = 2;
        String algorithmName = "md5";

        String encodedPassword = new SimpleHash(algorithmName, password, salt, times).toString();

        user.setSalt(salt);
        user.setPassword(encodedPassword);

        userService.add(user);

        return Result.success();
    }

    @PostMapping("/forelogin")
    public Object login(@RequestBody User userParam, HttpSession session) {
        String name = userParam.getName();
        name = HtmlUtils.htmlEscape(name);

        Subject subject = SecurityUtils.getSubject();
        UsernamePasswordToken token = new UsernamePasswordToken(name, userParam.getPassword());
        try {
            subject.login(token);
            User user = userService.getByName(name);
//          subject.getSession().setAttribute("user", user);
            session.setAttribute("user", user);
            return Result.success();
        } catch (AuthenticationException e) {
            String message = "账号密码错误";
            return Result.fail(message);
        }
    }

    @GetMapping("/forelogout")
    public String logout() {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated())
            subject.logout();
        return "redirect:home";
    }

    @GetMapping("/foreproduct/{pid}")
    public Object product(@PathVariable("pid") int pid) {
        Product product = productService.get(pid);
        List<ProductImage> singleProductImages = productImageService.listSingleProductImages(product);
        List<ProductImage> listDetailProductImages = productImageService.listDetailProductImages(product);
        product.setProductSingleImages(singleProductImages);
        product.setProductDetailImages(listDetailProductImages);

        List<PropertyValue> propertyValues = propertyValueService.list(product);
        List<Review> reviews = reviewService.list(product);
        productService.setSaleAndReviewNumber(product);
        productImageService.setFirstProductImage(product);

        Map<String, Object> map = new HashMap<>();
        map.put("product", product);
        map.put("pvs", propertyValues);
        map.put("reviews", reviews);

        return Result.success(map);
    }

    @GetMapping("forecheckLogin")
    public Object checkLogin() {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated())
            return Result.success();
        else
            return Result.fail("未登录");
    }

    @GetMapping("forecategory/{cid}")
    public Object category(@PathVariable int cid, String sort) {
        Category c = categoryService.get(cid);
        productService.fill(c);
        productService.setSaleAndReviewNumber(c.getProducts());
        categoryService.removeCategoryFromProduct(c);
        if (null != sort) {
            switch (sort) {
                case "review":
                    c.getProducts().sort(new ProductReviewComparator());
                    break;
                case "date":
                    c.getProducts().sort(new ProductDateComparator());
                    break;

                case "saleCount":
                    c.getProducts().sort(new ProductSaleCountComparator());
                    break;

                case "price":
                    c.getProducts().sort(new ProductPriceComparator());
                    break;

                case "all":
                    c.getProducts().sort(new ProductAllComparator());
                    break;
            }
        }
        return c;
    }

    @PostMapping("foresearch")
    public Object search(String keyword) {
        if (null == keyword)
            keyword = "";
        List<Product> ps = productService.search(keyword, 0, 20);
        productImageService.setFirstProductImages(ps);
        productService.setSaleAndReviewNumber(ps);
        return ps;
    }

    //立即购买
    @GetMapping("forebuyone")
    public Object buyOne(int pid, int num, HttpSession session) {
        return buyOneAndAddCart(pid, num, session);
    }

    private int buyOneAndAddCart(int pid, int num, HttpSession session) {
        Product product = productService.get(pid);
        User user = (User) session.getAttribute("user");
        List<OrderItem> orderItems = orderItemService.listByUser(user);
        for (OrderItem orderItem : orderItems) {
            if (orderItem.getProduct().getId() == product.getId()) {
                orderItem.setNumber(orderItem.getNumber() + num);
                orderItemService.update(orderItem);
                return orderItem.getId();
            }
        }

        OrderItem newOrderItem = new OrderItem();
        newOrderItem.setUser(user);
        newOrderItem.setProduct(product);
        newOrderItem.setNumber(num);
        orderItemService.add(newOrderItem);
        return newOrderItem.getId();
    }

    //结算
    @GetMapping("forebuy")
    public Object buy(String[] oiid, HttpSession session) {
        float total = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        for (String s : oiid) {
            int parseInt = Integer.parseInt(s);
            OrderItem orderItem = orderItemService.get(parseInt);
            total += orderItem.getProduct().getPromotePrice() * orderItem.getNumber();
            orderItems.add(orderItem);
        }
        productImageService.setFirstProductImagesOnOrderItems(orderItems);
        session.setAttribute("ois", orderItems);

        HashMap<String, Object> map = new HashMap<>();
        map.put("orderItems", orderItems);
        map.put("total", total);
        return Result.success(map);
    }

    //添加购物车
    @GetMapping("foreaddCart")
    public Object addCart(int pid, int num, HttpSession session) {
        buyOneAndAddCart(pid, num, session);
        return Result.success();
    }

    //查看购物车
    @GetMapping("forecart")
    public Object cart(HttpSession session) {
        User user = (User) session.getAttribute("user");
        List<OrderItem> orderItems = orderItemService.listByUser(user);
        productImageService.setFirstProductImagesOnOrderItems(orderItems);
        return orderItems;
    }

    @GetMapping("forechangeOrderItem")
    public Object changeOrderItem(HttpSession session, int pid, int num) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("未登录");
        }
        List<OrderItem> orderItems = orderItemService.listByUser(user);
        for (OrderItem orderItem : orderItems) {
            if (orderItem.getProduct().getId() == pid) {
                orderItem.setNumber(num);
                orderItemService.update(orderItem);
                break;
            }
        }
        return Result.success();
    }

    @GetMapping("foredeleteOrderItem")
    public Object deleteOrderItem(HttpSession session, int oiid) {
        User user = (User) session.getAttribute("user");
        if (null == user)
            return Result.fail("未登录");
        orderItemService.delete(oiid);
        return Result.success();
    }

    @PostMapping("forecreateOrder")
    public Object createOrder(@RequestBody Order order, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.fail("未登录");
        }
        String orderCode = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + RandomUtils.nextInt(10000);
        order.setOrderCode(orderCode);
        order.setCreateDate(new Date());
        order.setUser(user);
        order.setStatus(OrderService.waitPay);
        List<OrderItem> ois = (List<OrderItem>) session.getAttribute("ois");
        float total = orderService.add(order, ois);
        Map<String, Object> map = new HashMap<>();
        map.put("oid", order.getId());
        map.put("total", total);
        return Result.success(map);
    }

    //查看订单
    @GetMapping("forebought")
    public Object bought(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (null == user)
            return Result.fail("未登录");
        List<Order> os = orderService.listByUserWithoutDelete(user);
        orderService.removeOrderFromOrderItem(os);
        return os;
    }


    @GetMapping("foreconfirmPay")
    public Object confirmPay(int oid) {
        Order o = orderService.get(oid);
        orderItemService.fill(o);
//        orderService.cacl(o);
        orderService.removeOrderFromOrderItem(o);
        return o;
    }

    @GetMapping("foreorderConfirmed")
    public Object orderConfirmed(int oid) {
        Order order = orderService.get(oid);
        order.setStatus(OrderService.waitReview);
        order.setConfirmDate(new Date());
        orderService.update(order);
        return Result.success();
    }

    @PutMapping("foredeleteOrder")
    public Object deleteOrder(int oid) {
        Order order = orderService.get(oid);
        order.setStatus(OrderService.delete);
        orderService.update(order);
        return Result.success();
    }

    @GetMapping("forereview")
    public Object review(int oid) {
        Order order = orderService.get(oid);
        orderItemService.fill(order);
        orderService.removeOrderFromOrderItem(order);
        Product product = order.getOrderItems().get(0).getProduct();
        List<Review> reviews = reviewService.list(product);
        productService.setSaleAndReviewNumber(product);
        HashMap<String, Object> map = new HashMap<>();
        map.put("o", order);
        map.put("p", product);
        map.put("reviews", reviews);
        return Result.success(map);
    }

    @PostMapping("foredoreview")
    public Object doreview(HttpSession session, int oid, int pid, String content) {
        Order order = orderService.get(oid);
        order.setStatus(OrderService.finish);
        orderService.update(order);

        Product product = productService.get(pid);
        User user = (User) session.getAttribute("user");
        content = HtmlUtils.htmlEscape(content);

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setContent(content);
        review.setCreateDate(new Date());
        reviewService.add(review);
        return Result.success();
    }

    @GetMapping("forepayed")
    public Object payed(int oid) {
        Order order = orderService.get(oid);
        order.setStatus(OrderService.waitDelivery);
        order.setPayDate(new Date());
        orderService.update(order);
        return order;
    }
}
