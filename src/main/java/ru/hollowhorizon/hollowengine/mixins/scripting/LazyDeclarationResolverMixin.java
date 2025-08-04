package ru.hollowhorizon.hollowengine.mixins.scripting;

import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.incremental.components.LookupLocation;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.lazy.LazyDeclarationResolver;
import org.jetbrains.kotlin.resolve.scopes.MemberScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// По какой-то непонятной причине он не видит импортированные классы через BindingContext, но через getContributedClassifier(..) всё работает.
@Mixin(value=LazyDeclarationResolver.class, remap = false)
public abstract class LazyDeclarationResolverMixin {
    @Shadow
    public abstract MemberScope getMemberScopeDeclaredIn$frontend(KtDeclaration declaration, LookupLocation location);
    @Shadow protected abstract BindingContext getBindingContext();

    @Inject(method = "findClassDescriptorIfAny", at = @At("HEAD"), cancellable = true)
    private void onResolve(KtNamedDeclaration classObjectOrScript, LookupLocation location, CallbackInfoReturnable<ClassDescriptor> cir) {
        var scope = getMemberScopeDeclaredIn$frontend(classObjectOrScript, location);

        // Why not use the result here. Because it may be that there is a redeclaration:
        //     class A {} class A { fun foo(): A<completion here>}
        // and if we find the class by name only, we may b-not get the right one.
        // This call is only needed to make sure the classes are written to trace
        scope.getContributedClassifier(classObjectOrScript.getNameAsSafeName(), location);
        var descriptor = getBindingContext().get(BindingContext.DECLARATION_TO_DESCRIPTOR, classObjectOrScript);

        if(descriptor instanceof ClassDescriptor) cir.setReturnValue((ClassDescriptor) descriptor);
        else cir.setReturnValue((ClassDescriptor) scope.getContributedClassifier(classObjectOrScript.getNameAsSafeName(), location));
    }
}
