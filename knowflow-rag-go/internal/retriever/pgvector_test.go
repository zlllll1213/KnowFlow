package retriever

import "testing"

func TestKeywordTermsMixedChineseAndASCII(t *testing.T) {
	terms := keywordTerms("KnowFlow 是什么？")
	want := map[string]bool{
		"KnowFlow": true,
		"是什么":      true,
		"是什":       true,
		"什么":       true,
	}
	for _, term := range terms {
		delete(want, term)
	}
	if len(want) > 0 {
		t.Fatalf("missing terms: %#v; got=%v", want, terms)
	}
}

func TestKeywordTermsDropsSingleRuneNoise(t *testing.T) {
	terms := keywordTerms("a")
	if len(terms) != 0 {
		t.Fatalf("expected no useful terms, got=%v", terms)
	}
}
