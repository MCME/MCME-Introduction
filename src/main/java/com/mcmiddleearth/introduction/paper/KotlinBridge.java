package com.mcmiddleearth.introduction.paper;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class KotlinBridge {

    public static void info() throws Exception {
        // 1. Get the Kotlin Companion object
        Class<?> queryClass = Class.forName("com.typewritermc.core.entries.Query");
        Class<?> entryClass = Class.forName("com.typewritermc.core.entries.Entry");
        Class<?> kclass = Class.forName("kotlin.reflect.KClass");

        Object companion = queryClass.getField("Companion").get(null);

        // 2. Get the Companion's class (Query$Companion)
        Class<?> companionClass = companion.getClass();

        // 3. Get the "find" method
        Method findMethod = companionClass.getMethod(
                "find", kclass
        );

        // 4. Prepare KClass argument
        //KClass<?> kclass = JvmClassMappingKt.getKotlinClass(entryClass);

        // 5. Invoke reflectively
        Object result = findMethod.invoke(companion, kclass);

        Logger.getGlobal().info(result.getClass().getName());
        // 6. The result is a Kotlin Sequence<SomeEntry>
        //kotlin.sequences.Sequence<?> sequence = (kotlin.sequences.Sequence<?>) result;
        // 7. Iterate (example)
        //sequence.iterator().forEachRemaining(e -> Logger.getGlobal().info("E: "+e));
        /*for (Object e : sequence) {
            System.out.println(e);
        }*/
    }
}
