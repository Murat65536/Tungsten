package kaptainwutax.tungsten.transformer;

import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CASM;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Transformer that adds null constructors and method nullification to the ClientPlayerEntity hierarchy.
 * Each class gets its own transformer to avoid issues with multi-target transformations.
 */
public class ClientPlayerEntityHierarchyTransformer {

    private static final String ENTITY_CLASS = "net/minecraft/entity/Entity";
    private static final String HOLLOW_FIELD = "tungsten$isHollow";

    @CTransformer({
        Entity.class,
        LivingEntity.class,
        PlayerEntity.class,
        AbstractClientPlayerEntity.class,
        ClientPlayerEntity.class
    })
    public static class CommonTransformer {
        @CASM
        public static void transform(ClassNode node) {
            addNullConstructor(node);
            nullifyMethods(node);
        }
    }

    @CTransformer(Entity.class)
    public static class EntitySpecifics {
        @CASM
        public static void transform(ClassNode node) {
            // Add the hollow field to Entity class
            TransformerUtils.addBooleanField(node, HOLLOW_FIELD, Opcodes.ACC_PUBLIC);
        }
    }

    @CTransformer(ClientPlayerEntity.class)
    public static class ClientPlayerSpecifics {
        @CASM
        public static void transform(ClassNode node) {
            // Enhance null constructor to set hollow flag to true
            enhanceNullConstructor(node);
        }
    }

    public static void enhanceNullConstructor(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("<init>") && method.desc.equals("()V")) {
                AbstractInsnNode returnNode = null;
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        returnNode = insn;
                        break;
                    }
                }

                if (returnNode != null) {
                    InsnList setHollowFlag = new InsnList();
                    setHollowFlag.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
                    setHollowFlag.add(new InsnNode(Opcodes.ICONST_1)); // true
                    setHollowFlag.add(new FieldInsnNode(
                        Opcodes.PUTFIELD,
                        ENTITY_CLASS,
                        HOLLOW_FIELD,
                        "Z"
                    ));
                    method.instructions.insertBefore(returnNode, setHollowFlag);
                }
                break;
            }
        }
    }

    public static void addNullConstructor(ClassNode node) {
        // Check if a null constructor already exists
        for (MethodNode method : node.methods) {
            if (method.name.equals("<init>") && method.desc.equals("()V")) {
                return;
            }
        }

        MethodNode constructor = new MethodNode(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null
        );

        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
            Opcodes.INVOKESPECIAL,
            node.superName,
            "<init>",
            "()V",
            false
        ));

        // Set fields to null/0
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 || (field.access & Opcodes.ACC_FINAL) != 0) {
                continue;
            }

            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(TransformerUtils.getDefaultValueInstruction(field.desc));
            instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD,
                node.name,
                field.name,
                field.desc
            ));
        }

        instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.instructions = instructions;
        node.methods.add(constructor);
    }

    public static void nullifyMethods(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!TransformerUtils.shouldTransformMethod(method)) {
                continue;
            }

            // Special handling for tickMovement: Make it NPE safe
            if (method.name.equals("tickMovement")) {
                TransformerUtils.makeMethodNPESafe(method);
                continue;
            }

            // Insert check for tungsten$isHollow at the beginning of the method
            InsnList checkList = TransformerUtils.createFieldCheck(ENTITY_CLASS, HOLLOW_FIELD, method.desc);
            method.instructions.insert(checkList);
        }
    }
}