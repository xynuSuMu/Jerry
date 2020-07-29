package scan;

import annotation.JerryAutowired;
import annotation.JerryController;
import annotation.JerryRequestMapping;
import annotation.JerryService;
import context.JerryContext;
import exception.JerryException;
import handler.JerryHandlerMethod;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author 陈龙
 * @version 1.0
 * @date 2020-07-29 10:49
 */
public class ComponentScan {

    private static JerryContext jerryContext = JerryContext.getInstance();


    private List<Class<?>> cls = new CopyOnWriteArrayList<>();
    private List<Entry> entries = new CopyOnWriteArrayList<>();

    private String url;
    private String pkg;


    public ComponentScan(String url, String pkg) {
        this.url = url;
        this.pkg = pkg;
    }

    public void scanComponent(String url, String pkg) throws Exception {
        //然后把classpath和basePack合并
        String searchPath = url + pkg;
        scanComponent(new File(searchPath));
        //处理请求
        handlerController();
        //处理接口
        handlerInterface();
        //销毁
        cls = null;
        entries = null;
    }

    public void scanComponent(File file) throws Exception {
        if (file.isDirectory()) {//文件夹
            //文件夹我们就递归
            File[] files = file.listFiles();
            for (File f1 : files) {
                scanComponent(f1);
            }
        } else {
            //判断是否是class文件
            if (file.getName().endsWith(".class")) {
                //如果是class文件我们就放入我们的集合中。
                String pkg = file.getPath()
                        .replace(url, "")
                        .replace("/", ".");
                pkg = pkg.substring(0, pkg.length() - 6);
                Class<?> clazz = Class.forName(pkg);
                //如果是存在Controller注解-暂存
                JerryController jerryController = clazz.getAnnotation(JerryController.class);
                if (jerryController != null) {
                    cls.add(clazz);
                }
                JerryService jerryService = clazz.getAnnotation(JerryService.class);
                if (jerryService != null) {
                    String value = jerryService.value();
                    handlerService(clazz, value);
                }
            }
        }
    }

    //处理Service层
    private void handlerService(Class<?> clazz, String annotationValue) throws Exception {
        //判断是否为接口
        boolean isInterface = clazz.isInterface();
        Object o = null;
        if (isInterface) {
            //TODO：暂不支持寻找其实现类
        } else {
            o = clazz.newInstance();
            String beanId;
            if (annotationValue != null && !"".equals(annotationValue)) {
                beanId = annotationValue;
            } else {
                //其首字母小写
                String name = clazz.getName();
                beanId = name.substring(0, 1).toLowerCase() + name.substring(1);
            }
            if (jerryContext.getBean(beanId) != null) {
                throw new JerryException(beanId + "重复");
            }
            jerryContext.setBean(beanId, o);
        }

        if (o != null) {
            Field[] fields = clazz.getDeclaredFields();
            //处理字段，判断是否需要DI
            handlerField(o, fields);
        }
    }

    private void handlerField(Object instance, Field[] fields) {
        for (Field field : fields) {
            handlerDI(field, instance);
        }
    }

    //处理控制层方法
    private void handlerController() throws Exception {
        for (Class<?> clazz : cls) {
            Object o = clazz.newInstance();
            //为字段赋值
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                handlerDI(field, o);
            }
            //方法路径
            String requestMethodUrl = "";
            //是否有RequestMapping注解
            JerryRequestMapping jerryRequestMapping;
            if ((jerryRequestMapping = clazz.getAnnotation(JerryRequestMapping.class)) != null) {
                requestMethodUrl += jerryRequestMapping.value();
            }
            //获取该类中RequestMapping注解
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if ((jerryRequestMapping = method.getAnnotation(JerryRequestMapping.class)) != null) {
                    JerryHandlerMethod jerryHandlerMethod =
                            new JerryHandlerMethod(method,
                                    o,
                                    method.getParameters(),
                                    method.getReturnType(),
                                    jerryRequestMapping.method());
                    String requestMapping = requestMethodUrl + jerryRequestMapping.value();
                    if (jerryContext.getMethod(requestMapping) != null) {
                        throw new JerryException("requestMapping重复:" + requestMapping);
                    } else {
                        jerryContext.setControllerMethod(requestMapping,
                                jerryHandlerMethod);
                    }
                }
            }
        }
    }

    //进行DI
    private void handlerDI(Field field, Object o) {
        JerryAutowired jerryAutowired = field.getDeclaredAnnotation(JerryAutowired.class);
        if (jerryAutowired != null) {
            String beanName = jerryAutowired.name();
            String beanId;
            field.setAccessible(true);
            Object instance = null;
            //如果指定了注入的类
            if (beanName != null && !"".equals(beanName)) {
                beanId = beanName;
                instance = jerryContext.getBean(beanId);
            }
            //如果未在Autowired指定name,则根据包名来找
            if (instance == null) {
                //使用Type包含包名，预防不同包下相同名称的类
                boolean isInterface = field.getType().isInterface();
                if (isInterface) {
                    //接口字段，暂存
                    Entry entry = new Entry(field, o);
                    entries.add(entry);
                    return;
                } else {
                    String name = field.getType().getName();
                    beanId = name.substring(0, 1).toLowerCase() + name.substring(1);
                    instance = jerryContext.getBean(beanId);
                }
            }
            try {
                field.set(o, instance);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    //处理接口注入的字段
    private void handlerInterface() {
        List<Object> list = jerryContext.getBeans();
        entries.stream().forEach(entry -> {
            try {
                entry.handler(list);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    class Entry {
        private Field field;
        private Object object;

        public Entry(Field field, Object object) {
            this.field = field;
            this.object = object;
        }

        public void handler(List<Object> list) throws IllegalAccessException {
            String beanName = field.getAnnotation(JerryAutowired.class).name();

            if (beanName != null && !"".equals(beanName)) {
                //寻找Bean
                field.set(object, jerryContext.getBean(beanName));
            } else {
                for (Object o : list) {
                    for (Class<?> cls : o.getClass().getInterfaces()) {
                        if (cls == field.getType()) {
                            field.set(object, o);
                            return;
                        }
                    }
                }
            }
        }
    }
}