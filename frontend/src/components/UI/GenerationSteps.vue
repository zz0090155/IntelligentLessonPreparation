<script setup>
const props = defineProps({
  steps: {
    type: Array,
    default: () => [],
  },
  activeStep: {
    type: Number,
    default: 0,
  },
})
</script>

<template>
  <div class="generation-steps">
    <div
      v-for="(step, idx) in props.steps"
      :key="step + idx"
      class="generation-step"
      :class="{ active: idx <= props.activeStep }"
    >
      <div class="generation-step__index">
        {{ idx + 1 }}
      </div>
      <span>{{ step }}</span>
      <span
        v-if="idx !== props.steps.length - 1"
        class="generation-step__line"
        :class="{ done: idx < props.activeStep }"
      ></span>
    </div>
  </div>
</template>

<style scoped>
.generation-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.generation-step {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: rgba(203, 213, 225, 0.62);
  font-size: 12px;
}

.generation-step.active {
  color: #dbeafe;
  font-weight: 700;
}

.generation-step__index {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(6, 13, 43, 0.52);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.generation-step.active .generation-step__index {
  border-color: rgba(56, 189, 248, 0.62);
  color: #fff;
  background: linear-gradient(145deg, #2bd4ff, #347cff 48%, #7b2cff);
  box-shadow: 0 14px 30px -18px rgba(56, 189, 248, 0.9);
}

.generation-step__line {
  width: 34px;
  height: 1px;
  background: rgba(148, 163, 184, 0.22);
}

.generation-step__line.done {
  background: linear-gradient(90deg, #38d5ff, #7b5cff);
}
</style>
