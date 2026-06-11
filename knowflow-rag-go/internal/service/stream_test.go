package service

import "testing"

func TestNewAgentMetaChannelBuffersInitialAndFinalMeta(t *testing.T) {
	ch := newAgentMetaChannel()

	if cap(ch) != 2 {
		t.Fatalf("expected meta channel capacity 2, got %d", cap(ch))
	}
}
