// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vector>
#include <string>
#include "fakeposting.h"

namespace search
{

namespace fakedata
{

class FakeWord;
class FakeWordSet;

class FPFactory
{
public:
    virtual
    ~FPFactory(void);

    virtual FakePosting::SP
    make(const FakeWord &fw) = 0;

    virtual void
    setup(const FakeWordSet &fws);

    virtual void
    setup(const std::vector<const FakeWord *> &fws);
};

template<class P>
class FPFactoryT : public FPFactory
{
public:
    FPFactoryT(const index::Schema &schema)
        : FPFactory()
    {
        (void) schema;
    }

    FakePosting::SP make(const FakeWord &fw) override {
        return FakePosting::SP(new P(fw));
    }
};

typedef FPFactory *(FPFactoryMaker)(const index::Schema &schema);

typedef std::pair<const std::string, FPFactoryMaker *const>
FPFactoryMapEntry;

template <class F>
static FPFactory *
makeFPFactory(const index::Schema &schema)
{
    return new F(schema);
}

FPFactory *
getFPFactory(const std::string &name, const index::Schema &schema);

std::vector<std::string>
getPostingTypes(void);

class FPFactoryInit
{
    std::string _key;
public:
    FPFactoryInit(const FPFactoryMapEntry &fpFactoryMapEntry);

    ~FPFactoryInit(void);

    static void
    forceLink(void);
};

} // namespace fakedata

} // namespace search

