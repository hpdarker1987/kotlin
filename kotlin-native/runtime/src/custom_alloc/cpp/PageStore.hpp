/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_PAGESTORE_HPP_
#define CUSTOM_ALLOC_CPP_PAGESTORE_HPP_

#include <atomic>

#include "AtomicStack.hpp"

namespace kotlin::alloc {

template <class T>
class PageStore {
public:
    void PrepareForGC() noexcept {
        unswept_.TransferAllFrom(ready_);
        unswept_.TransferAllFrom(used_);
        T* page;
        while ((page = empty_.Pop())) page->Destroy();
    }

    void Sweep() noexcept {
        while (SweepAndFreeEmpty(unswept_, ready_)) {}
    }

    T* GetPage(uint32_t cellCount) noexcept {
        T* page;
        if ((page = SweepAndFreeEmpty(unswept_, used_))) {
            return page;
        }
        if ((page = ready_.Pop())) {
            used_.Push(page);
            return page;
        }
        if ((page = empty_.Pop())) {
            used_.Push(page);
            return page;
        }
        return NewPage(cellCount);
    }

    T* NewPage(uint64_t cellCount) noexcept {
        T* page = T::Create(cellCount);
        used_.Push(page);
        return page;
    }

    ~PageStore() noexcept {
        T* page;
        while ((page = empty_.Pop())) page->Destroy();
        while ((page = ready_.Pop())) page->Destroy();
        while ((page = used_.Pop())) page->Destroy();
        while ((page = unswept_.Pop())) page->Destroy();
    }

private:
    T* SweepAndFreeEmpty(AtomicStack<T>& from, AtomicStack<T>& to) noexcept {
        T* page;
        while ((page = from.Pop())) {
            if (!page->Sweep()) {
                CustomAllocInfo("SweepAndFreeEmpty free(%p)", page);
                empty_.Push(page);
            } else {
                to.Push(page);
                return page;
            }
        }
        return nullptr;
    }

    AtomicStack<T> empty_;
    AtomicStack<T> ready_;
    AtomicStack<T> used_;
    AtomicStack<T> unswept_;
};

} // namespace kotlin::alloc

#endif
