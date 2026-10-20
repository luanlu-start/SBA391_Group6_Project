import { z } from 'zod';

export function createPasswordSchema(minLength = 8) {
  return z.string().refine(value => [...value].length >= minLength
    && new TextEncoder().encode(value).length <= 72
    && /\p{Lu}/u.test(value) && /\p{Ll}/u.test(value) && /\p{Nd}/u.test(value),
  `Mật khẩu cần ít nhất ${minLength} ký tự, có chữ hoa, chữ thường, chữ số và tối đa 72 byte.`);
}
