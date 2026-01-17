package kaptainwutax.tungsten.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;

/**
 * Utility class for common ASM transformation operations.
 * Shared between different transformers to avoid code duplication.
 */
public final class TransformerUtils {

    private TransformerUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Methods from java.lang.Object that should not be nullified.
     */
    public static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
        "toString", "equals", "hashCode", "clone", "finalize",
        "getClass", "notify", "notifyAll", "wait"
    ));

    /**
     * strict NPE protection for a method.
     * Wraps GETFIELD, PUTFIELD, INVOKEVIRTUAL, INVOKEINTERFACE in null checks.
     * If the target object is null, the instruction is skipped and default values are pushed/popped.
     */
    public static void makeMethodNPESafe(MethodNode method) {
        InsnList instructions = method.instructions;

        for (AbstractInsnNode insn : instructions) {
            if (insn.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                injectGetFieldProtection(instructions, fieldInsn);
            } else if (insn.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                injectPutFieldProtection(instructions, fieldInsn);
            } else if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                injectInvokeProtection(instructions, methodInsn);
            }
        }
    }

    private static void injectGetFieldProtection(InsnList instructions, FieldInsnNode fieldInsn) {
        InsnList list = new InsnList();
        
        // Stack: [obj]
        list.add(new InsnNode(Opcodes.DUP)); // [obj, obj]
        
        LabelNode safeLabel = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFNONNULL, safeLabel)); // [obj]
        
        // Case: obj is null
        list.add(new InsnNode(Opcodes.POP)); // [] (empty)
        list.add(getDefaultValueInstruction(fieldInsn.desc)); // [defaultValue]
        
        LabelNode doneLabel = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));
        
        list.add(safeLabel); // [obj]
        
        instructions.insertBefore(fieldInsn, list);
        instructions.insert(fieldInsn, doneLabel);
    }

    private static void injectPutFieldProtection(InsnList instructions, FieldInsnNode fieldInsn) {
        InsnList list = new InsnList();
        boolean isDoubleSlot = fieldInsn.desc.equals("J") || fieldInsn.desc.equals("D");
        
        if (isDoubleSlot) {
            // Stack: [obj, val_low, val_high]
            list.add(new InsnNode(Opcodes.DUP2_X1)); // [val_low, val_high, obj, val_low, val_high]
            list.add(new InsnNode(Opcodes.POP2));    // [val_low, val_high, obj]
            list.add(new InsnNode(Opcodes.DUP));     // [val_low, val_high, obj, obj]
            
            LabelNode safeLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.IFNONNULL, safeLabel)); // [val_low, val_high, obj]
            
            // Case: obj is null
            list.add(new InsnNode(Opcodes.POP));     // [val_low, val_high]
            list.add(new InsnNode(Opcodes.POP2));    // []
            
            LabelNode doneLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));
            
            list.add(safeLabel); // [val_low, val_high, obj]
            // Restore stack for PUTFIELD: [obj, val_low, val_high]
            list.add(new InsnNode(Opcodes.DUP_X2));  // [obj, val_low, val_high, obj]
            list.add(new InsnNode(Opcodes.POP));     // [obj, val_low, val_high]
            
            instructions.insertBefore(fieldInsn, list);
            instructions.insert(fieldInsn, doneLabel);
        } else {
            // Stack: [obj, val]
            list.add(new InsnNode(Opcodes.SWAP));    // [val, obj]
            list.add(new InsnNode(Opcodes.DUP));     // [val, obj, obj]
            
            LabelNode safeLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.IFNONNULL, safeLabel)); // [val, obj]
            
            // Case: obj is null
            list.add(new InsnNode(Opcodes.POP));     // [val]
            list.add(new InsnNode(Opcodes.POP));     // []
            
            LabelNode doneLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));
            
            list.add(safeLabel); // [val, obj]
            // Restore stack for PUTFIELD: [obj, val]
            list.add(new InsnNode(Opcodes.SWAP));    // [obj, val]
            
            instructions.insertBefore(fieldInsn, list);
            instructions.insert(fieldInsn, doneLabel);
        }
    }

    private static void injectInvokeProtection(InsnList instructions, MethodInsnNode methodInsn) {
        Type[] argumentTypes = Type.getArgumentTypes(methodInsn.desc);
        
        // Case 0: No arguments
        if (argumentTypes.length == 0) {
            InsnList list = new InsnList();
            // Stack: [obj]
            list.add(new InsnNode(Opcodes.DUP)); // [obj, obj]
            
            LabelNode safeLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.IFNONNULL, safeLabel)); // [obj]
            
            // Case: obj is null
            list.add(new InsnNode(Opcodes.POP)); // [] (empty)
            
            // Push default return value
            String returnType = methodInsn.desc.substring(methodInsn.desc.indexOf(')') + 1);
            if (!returnType.equals("V")) { // If not void
                 list.add(getDefaultValueInstruction(returnType));
            }
            
            LabelNode doneLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));
            
            list.add(safeLabel); // [obj]
            
            instructions.insertBefore(methodInsn, list);
            instructions.insert(methodInsn, doneLabel);
            return;
        }

        // Case 1: 1 argument (and single slot)
        if (argumentTypes.length == 1 && argumentTypes[0].getSize() == 1) {
            InsnList list = new InsnList();
            // Stack: [obj, arg]
            list.add(new InsnNode(Opcodes.SWAP));    // [arg, obj]
            list.add(new InsnNode(Opcodes.DUP));     // [arg, obj, obj]
            
            LabelNode safeLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.IFNONNULL, safeLabel)); // [arg, obj]
            
            // Case: obj is null
            list.add(new InsnNode(Opcodes.POP));     // [arg]
            list.add(new InsnNode(Opcodes.POP));     // []
            
            // Push default return value
            String returnType = methodInsn.desc.substring(methodInsn.desc.indexOf(')') + 1);
            if (!returnType.equals("V")) { // If not void
                 list.add(getDefaultValueInstruction(returnType));
            }
            
            LabelNode doneLabel = new LabelNode();
            list.add(new JumpInsnNode(Opcodes.GOTO, doneLabel));
            
            list.add(safeLabel); // [arg, obj]
            list.add(new InsnNode(Opcodes.SWAP));    // [obj, arg]
            
            instructions.insertBefore(methodInsn, list);
            instructions.insert(methodInsn, doneLabel);
            return;
        }
        
        // For > 1 args or double/long args, protection is skipped for now to avoid complex stack manipulation.
    }

    /**
     * Generates the appropriate return instruction for a given method descriptor.
     * Returns default values (null, 0, false, etc.) based on return type.
     */
    public static InsnList getReturnInstruction(String methodDescriptor) {
        InsnList instructions = new InsnList();
        String returnType = methodDescriptor.substring(methodDescriptor.indexOf(')') + 1);

        switch (returnType.charAt(0)) {
            case 'V': // void
                instructions.add(new InsnNode(Opcodes.RETURN));
                break;
            case 'L': // Object
            case '[': // Array
                instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                instructions.add(new InsnNode(Opcodes.ARETURN));
                break;
            case 'Z': // boolean
            case 'B': // byte
            case 'C': // char
            case 'S': // short
            case 'I': // int
                instructions.add(new InsnNode(Opcodes.ICONST_0));
                instructions.add(new InsnNode(Opcodes.IRETURN));
                break;
            case 'J': // long
                instructions.add(new InsnNode(Opcodes.LCONST_0));
                instructions.add(new InsnNode(Opcodes.LRETURN));
                break;
            case 'F': // float
                instructions.add(new InsnNode(Opcodes.FCONST_0));
                instructions.add(new InsnNode(Opcodes.FRETURN));
                break;
            case 'D': // double
                instructions.add(new InsnNode(Opcodes.DCONST_0));
                instructions.add(new InsnNode(Opcodes.DRETURN));
                break;
            default:
                throw new RuntimeException("Unknown return type in descriptor: " + methodDescriptor);
        }

        return instructions;
    }

    /**
     * Generates the appropriate default value instruction for a field type.
     * Used when initializing fields to default values in constructors.
     */
    public static InsnNode getDefaultValueInstruction(String fieldDescriptor) {
        return switch (fieldDescriptor.charAt(0)) { // Object reference
            case 'L', '[' -> // Array
                    new InsnNode(Opcodes.ACONST_NULL);
            case 'Z', 'B', 'C', 'S', 'I' -> // boolean, byte, char, short, int
                    new InsnNode(Opcodes.ICONST_0);
            case 'J' -> // long
                    new InsnNode(Opcodes.LCONST_0);
            case 'F' -> // float
                    new InsnNode(Opcodes.FCONST_0);
            case 'D' -> // double
                    new InsnNode(Opcodes.DCONST_0);
            default -> new InsnNode(Opcodes.ACONST_NULL);
        };
    }

    /**
     * Checks if a method should be transformed.
     * Excludes constructors, static methods, abstract methods, and Object methods.
     */
    public static boolean shouldTransformMethod(MethodNode method) {
        // Skip constructors
        if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
            return false;
        }

        // Skip static methods
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return false;
        }

        // Skip abstract methods
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }

        // Skip Object methods
        if (OBJECT_METHODS.contains(method.name)) {
            return false;
        }

        return true;
    }

    /**
     * Adds a boolean field to a class if it doesn't already exist.
     */
    public static void addBooleanField(ClassNode node, String fieldName, int access) {
        // Check if field already exists
        for (FieldNode field : node.fields) {
            if (field.name.equals(fieldName)) {
                return;
            }
        }

        // Add the boolean field
        node.fields.add(new FieldNode(
            access,
            fieldName,
            "Z", // boolean descriptor
            null,
            null
        ));
    }

    /**
     * Creates a field check that returns early if a boolean field is true.
     * Used for creating "hollow" or "frozen" behavior.
     */
    public static InsnList createFieldCheck(String ownerClass, String fieldName, String methodDescriptor) {
        InsnList checkList = new InsnList();

        // Load 'this'
        checkList.add(new VarInsnNode(Opcodes.ALOAD, 0));

        // Get field value
        checkList.add(new FieldInsnNode(
            Opcodes.GETFIELD,
            ownerClass,
            fieldName,
            "Z"
        ));

        // Create label for continuing with normal method execution
        LabelNode continueLabel = new LabelNode();

        // If field is false, jump to continue
        checkList.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));

        // If field is true, return default value
        checkList.add(getReturnInstruction(methodDescriptor));

        // Add continue label
        checkList.add(continueLabel);

        return checkList;
    }

    /**
     * Gets the internal name for common Minecraft entity classes.
     * Helps with class hierarchy navigation.
     */
    public static String getInternalName(Class<?> clazz) {
        return clazz.getName().replace('.', '/');
    }

    /**
     * Checks if a class node represents a specific Minecraft class.
     */
    public static boolean isClass(ClassNode node, String className) {
        String nodeName = node.name.replace('/', '.');
        return nodeName.equals(className) || nodeName.endsWith("." + className);
    }
}