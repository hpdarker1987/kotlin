/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Heap.hpp"

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "CustomAllocConstants.hpp"
#include "AtomicStack.hpp"
#include "CustomLogging.hpp"
#include "ExtraObjectData.hpp"
#include "ExtraObjectPage.hpp"
#include "ThreadRegistry.hpp"
#include "GCImpl.hpp"

namespace kotlin::alloc {

Heap::~Heap() noexcept {
    ExtraObjectPage* page;
    while ((page = extraObjectPages_.Pop())) page->Destroy();
    while ((page = usedExtraObjectPages_.Pop())) page->Destroy();
}

void Heap::PrepareForGC() noexcept {
    CustomAllocDebug("Heap::PrepareForGC()");
    for (auto& thread : kotlin::mm::ThreadRegistry::Instance().LockForIter()) {
        thread.gc().impl().alloc().PrepareForGC();
    }

    mediumPages_.PrepareForGC();
    largePages_.PrepareForGC();
    for (int blockSize = 0; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE; ++blockSize) {
        smallPages_[blockSize].PrepareForGC();
    }
}

void Heap::Sweep() noexcept {
    CustomAllocDebug("Heap::Sweep()");
    for (int blockSize = 0; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE; ++blockSize) {
        smallPages_[blockSize].Sweep();
    }
    mediumPages_.Sweep();
    largePages_.Sweep();
}

size_t Heap::SweepExtraObjects(gc::GCHandle gcHandle, AtomicStack<mm::ExtraObjectData>& finalizerQueue) noexcept {
    CustomAllocDebug("Heap::SweepExtraObjects()");
    size_t finalizersScheduled = 0;
    ExtraObjectPage* page;
    while ((page = usedExtraObjectPages_.Pop())) {
        if (!page->Sweep(finalizerQueue, finalizersScheduled)) {
            CustomAllocInfo("SweepExtraObjects free(%p)", page);
            free(page);
        } else {
            extraObjectPages_.Push(page);
        }
    }
    return finalizersScheduled;
}

MediumPage* Heap::GetMediumPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("Heap::GetMediumPage()");
    return mediumPages_.GetPage(cellCount);
}

SmallPage* Heap::GetSmallPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("Heap::GetSmallPage()");
    return smallPages_[cellCount].GetPage(cellCount);
}

LargePage* Heap::GetLargePage(uint64_t cellCount) noexcept {
    CustomAllocInfo("CustomAllocator::AllocateInLargePage(%" PRIu64 ")", cellCount);
    return largePages_.NewPage(cellCount);
}

ExtraObjectPage* Heap::GetExtraObjectPage() noexcept {
    CustomAllocInfo("CustomAllocator::GetExtraObjectPage()");
    ExtraObjectPage* page = extraObjectPages_.Pop();
    if (page) {
        usedExtraObjectPages_.Push(page);
        return page;
    }
    page = ExtraObjectPage::Create();
    usedExtraObjectPages_.Push(page);
    return page;
}

} // namespace kotlin::alloc
