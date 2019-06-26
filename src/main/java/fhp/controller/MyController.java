package fhp.controller;

import fhp.annotation.Autowired;
import fhp.annotation.Controller;
import fhp.annotation.RequestMapping;
import fhp.annotation.RequestParameter;
import fhp.service.MyService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/my")
public class MyController {

    @Autowired
    private MyService myService;


    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @RequestParameter("name") String name) {
        String result = myService.getByName(name);

        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequestMapping("/add")
    public void add(HttpServletRequest req, HttpServletResponse resp,
                    @RequestParameter("a") int a, @RequestParameter("b") int b) {
        try {
            resp.getWriter().write(a+" + "+ b + " = "+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
