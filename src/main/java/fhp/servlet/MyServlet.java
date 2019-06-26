package fhp.servlet;

import fhp.annotation.Autowired;
import fhp.annotation.Controller;
import fhp.annotation.RequestMapping;
import fhp.annotation.Service;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 这个类作为启动入口
 */
public class MyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    //和web.xml中配置的param-name一致
    private static final String LOCATION = "contextConfigLocation";
    //保存所有的配置信息
    private Properties properties = new Properties();
    //保存所有被扫描到的相关类名
    private List<String> classNames = new ArrayList<String>();

    //核心IOC容器，保存所有初始化bean
    private Map<String, Object> ioc = new ConcurrentHashMap();

    //保存所有URL和方法的映射
    private Map<String, Method> handlerMapping = new ConcurrentHashMap<String, Method>();


    public MyServlet() {
        super();
    }

    /**
     * 初始化，加载配置文件
     *
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        System.out.println("为什么启动不了呀、");
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        //2.扫描包
        doScanner(properties.get("scanPackage"));
        //3.初始化所有相关类的实例，并保存到IOC容器中
        doInstance();
        //4.自动注入
        doAutowired();
        //5.构造HandlerMapping
        initHandlerMapping();

        //6.等待请求，匹配URL,定位方法，反射调用执行
        //调用doPost或者doGet

        //提示信息
        System.out.println("mySpring is init");

    }

    private void doLoadConfig(String location) {
        InputStream is = null;
        try {
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            //读取配置文件
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //递归扫描出所有Class文件
    private void doScanner(Object packageName) {
        URL url = this.getClass().getClassLoader()
                .getResource("/" + packageName.toString().replaceAll("\\.", "/"));
        File dir = new File(url.getFile());

        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                doScanner(packageName + "." + f.getName());
            } else {
                classNames.add(packageName + "." + f.getName().replace(".class", ""));
            }
        }
    }

    private void doInstance() {
        if (classNames.size() == 0) {
            return;
        }


        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    //默认将首字母小写作为bean
                    String beanName = lowerCaseFirstCase(className);
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    //如果用户自己设置了名称，就用用户自己设置的
                    if ("".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }
                    //如果自己没设置bean名称,那么就按照接口去创建实例【注入的时候是按照接口注入的】
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到实例对象中的所有属性【只有在ioc容器中的bean，才可以注入属性】
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Autowired autowired = field.getDeclaredAnnotation(Autowired.class);
                    String beanName = autowired.value().trim();
                    if ("".equals(beanName)) {
                        //如果Autowired没有声明注入的bean的名称，那么就按照类型注入
                        beanName = field.getType().getName();
                    }
                    //设置访问权限，不管是私有还是共有属性
                    field.setAccessible(true);

                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }

            String baseUrl = "";
            //获取Controller的url配置
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                //我迷惑了一会儿，但是要知道，注解中的value在编译期间就确定了，所以是可以获得的
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //获取方法的url配置
            String methodUrl = "";

            Method[] methods = entry.getValue().getClass().getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                    methodUrl = requestMapping.value().replaceAll("/", "");
                    String requestUrl = "/" + baseUrl + "/" + methodUrl;
                    handlerMapping.put(requestUrl, method);
                    System.out.println("mapped url : " + requestUrl + "-" + method);
                }
            }

        }

    }


    //这里默认所有类的名称都是大写开头
    private String lowerCaseFirstCase(String className) {
        char[] chars = className.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    /**
     * 执行业务处理
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doDispatch(req, resp);
        super.doPost(req, resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (handlerMapping.isEmpty()) return;

        String url = req.getRequestURI();

        String contextPath = req.getContextPath();

        url = url.replace(contextPath, "").replaceAll("/+", "/");

        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 not found");
            return;
        }

        //获取请求参数列表
        Map<String, String[]> params = req.getParameterMap();

        Method method = handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            //根据参数名称，进行处理
            Class paramType = parameterTypes[i];

            if (paramType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (paramType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (paramType == String.class) {
                for (Map.Entry<String, String[]> param : params.entrySet()) {
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
        }

        try {
            String beanName = lowerCaseFirstCase(method.getDeclaringClass().getSimpleName());
            method.invoke(ioc.get(beanName), paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
