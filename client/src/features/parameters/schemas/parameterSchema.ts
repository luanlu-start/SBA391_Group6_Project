import { z } from 'zod';
import type { SystemParameter } from '../../../types/parameter';

export function createParameterSchema(parameter: SystemParameter) {
  return z.object({
    value: z.string().trim().min(1, 'Nhập giá trị tham số.').max(1000, 'Giá trị tối đa 1.000 ký tự.')
      .superRefine((value, context) => {
        if (parameter.valueType === 'TEXT') return;
        const pattern = parameter.valueType === 'INTEGER' ? /^\d+$/ : /^\d+(\.\d+)?$/;
        if (!pattern.test(value) || !Number.isFinite(Number(value))) {
          context.addIssue({ code: 'custom', message: parameter.valueType === 'INTEGER'
            ? 'Nhập số nguyên không âm, không dùng dấu phân cách.'
            : 'Nhập số không âm; dùng dấu chấm cho phần thập phân.' });
        }
      }),
  });
}

export type ParameterFormValues = { value: string };
