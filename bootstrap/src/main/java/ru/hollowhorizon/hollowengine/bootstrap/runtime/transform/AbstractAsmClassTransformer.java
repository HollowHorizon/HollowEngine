package ru.hollowhorizon.hollowengine.bootstrap.runtime.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;

abstract class AbstractAsmClassTransformer implements RuntimeClassTransformer {
    private final String targetClassName;

    protected AbstractAsmClassTransformer(String targetClassName) {
        this.targetClassName = targetClassName;
    }

    @Override
    public final boolean supports(String className) {
        return targetClassName.equals(className);
    }

    @Override
    public final byte[] transform(String className, byte[] originalBytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(originalBytes).accept(classNode, 0);
        transform(classNode);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    protected abstract void transform(ClassNode classNode);

    protected static MethodNode requireMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        throw new IllegalStateException("Failed to find method " + name + descriptor + " in " + classNode.name);
    }

    protected static void replaceWithStaticCall(MethodNode method, String owner, String name, String descriptor) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        clearDebugMetadata(method);

        Type[] arguments = Type.getArgumentTypes(method.desc);
        int variableIndex = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

        InsnList instructions = new InsnList();
        for (Type argument : arguments) {
            instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), variableIndex));
            variableIndex += argument.getSize();
        }

        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, descriptor, false));
        instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));

        method.instructions.add(instructions);
        method.maxLocals = variableIndex;
        method.maxStack = Math.max(1, arguments.length + 1);
    }

    protected static void replaceWithReturn(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        clearDebugMetadata(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        method.maxStack = 0;
    }

    protected static void replaceWithNullReturn(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        clearDebugMetadata(method);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        method.maxStack = 1;
    }

    protected static void addInterface(ClassNode classNode, String internalName) {
        if (classNode.interfaces == null) {
            classNode.interfaces = new ArrayList<>();
        }
        if (!classNode.interfaces.contains(internalName)) {
            classNode.interfaces.add(internalName);
        }
    }

    protected static boolean hasMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    protected static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    protected static void addGetterBridge(ClassNode classNode, String name, String descriptor, String fieldName, String fieldDescriptor) {
        if (hasMethod(classNode, name, descriptor)) return;

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, fieldName, fieldDescriptor));
        method.instructions.add(new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
        method.maxLocals = 1;
        method.maxStack = 1;
        classNode.methods.add(method);
    }

    protected static void addSetterBridge(ClassNode classNode, String name, String descriptor, String fieldName, String fieldDescriptor) {
        if (hasMethod(classNode, name, descriptor)) return;

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Type.getArgumentTypes(descriptor)[0].getOpcode(Opcodes.ILOAD), 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, classNode.name, fieldName, fieldDescriptor));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        classNode.methods.add(method);
    }

    protected static void addInvokerBridge(ClassNode classNode, String name, String descriptor, String targetName, String targetDescriptor) {
        if (hasMethod(classNode, name, descriptor)) return;

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        Type[] arguments = Type.getArgumentTypes(descriptor);
        int variableIndex = 1;

        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (Type argument : arguments) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), variableIndex));
            variableIndex += argument.getSize();
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.name, targetName, targetDescriptor, false));
        method.instructions.add(new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
        method.maxLocals = variableIndex;
        method.maxStack = Math.max(1, arguments.length + 1);
        classNode.methods.add(method);
    }

    private static void clearDebugMetadata(MethodNode method) {
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }
}
